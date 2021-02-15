package com.zendesk.models

case class ZendeskTickets(id: Int,
                          description: String,
                          created_at: String,
                          updated_at: String,
                         ) {
  override def toString: String =
    s"Ticket ID: $id, Description: $description, Created Date: $created_at, Update Date: $updated_at"
}