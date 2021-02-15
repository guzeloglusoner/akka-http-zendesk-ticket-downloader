package com.zendesk.models

case class ZendeskTicketsStream(tickets: Seq[ZendeskTickets],
                                after_url: String,
                                after_cursor: String,
                                end_of_stream: Boolean)
