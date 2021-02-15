package com.zendesk

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ThrottleMode
import akka.stream.scaladsl._
import com.zendesk.models.{ZendeskDomain, ZendeskTickets, ZendeskTicketsStream}
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object ZendeskConsumer {

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "ZendeskConsumer")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    // Json format for domain models
    implicit val zendeskTicketsFormat: RootJsonFormat[ZendeskTickets] = jsonFormat4(ZendeskTickets)
    implicit val zendeskTicketsStreamFormat: RootJsonFormat[ZendeskTicketsStream] = jsonFormat4(ZendeskTicketsStream)
    implicit val zendeskDomainFormat: RootJsonFormat[ZendeskDomain] = jsonFormat3(ZendeskDomain)

    // Infinite stream to consume data
    val infiniteStream = Source.fromIterator(() =>
      Iterator.continually(()))

    // Throttling the fast source to stay with the limits
    val throttlingFlow = Flow[Unit].throttle(
      // how many elements do we allow
      elements = 1,
      // in what unit of time
      per = 10.second,
      maximumBurst = 0,
      // we can also set this to Enforcing, but then your
      // stream will collapse if exceeding the number of elements / s
      mode = ThrottleMode.Shaping
    )

    /**
     * This is the initial call performed to get all tickets till provided start_time and to start stream with cursor
     */
    val domainStreamInitialCursor = (zendeskDomain: ZendeskDomain) =>
      Http().singleRequest(
        HttpRequest(uri = s"https://${zendeskDomain.domain}.zendesk.com/api/v2/incremental/tickets/cursor.json?start_time=${zendeskDomain.startTime}")
          .addHeader(RawHeader("Authorization", s"Bearer ${zendeskDomain.oauth}")))


    /**
     * This is the routine stream we run with throttling enabled.
     *  - It first loads the cursor from initial call.
     *  - Then starts the Source of infinite stream with throttling.
     *  - cursor is the mutable state string to preserve cursor after each Source element emits.
     */
    val domainStreamStart = (zendeskDomain: ZendeskDomain, afterCursor: String) => {
      var cursor = afterCursor
      infiniteStream
        .via(throttlingFlow)
        .mapAsync(1) { _ =>
          Http()
            .singleRequest(
              HttpRequest(uri = s"https://${zendeskDomain.domain}.zendesk.com/api/v2/incremental/tickets/cursor.json?cursor=$cursor")
                .addHeader(RawHeader("Authorization", s"Bearer ${zendeskDomain.oauth}")))
        }
        .mapAsync(1)(resp => Unmarshal(resp.entity).to[ZendeskTicketsStream])
        .runWith(Sink.foreach(e => {
          cursor = e.after_cursor
          e.tickets.foreach(t => println(s"Domain: ${zendeskDomain.domain}, ${t.toString}"))
        }))
    }

    /**
     * Route to get ZendeskDomain object and start-time from client and starts the stream.
     */
    val route =
      path("startConsumer") {
        post {
          entity(as[ZendeskDomain]) { domain =>
            domainStreamInitialCursor(domain).flatMap(e =>
              Unmarshal(e.entity).to[ZendeskTicketsStream].flatMap(e => {
                e.tickets.foreach(t => println(s"Domain: ${domain.domain}, ${t.toString}"))
                domainStreamStart(domain, e.after_cursor)
              })
            )
            complete(
              HttpResponse(StatusCodes.OK)
            )
          }
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
