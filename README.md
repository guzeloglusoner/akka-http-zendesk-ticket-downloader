# Zendesk Ticket Downloader with Akka HTTP

The aim of the project is to stream ZenDesk Tickets in real-time.

## Getting Started

To build such system, Akka HTTP Version 10.2.3 is implemented.

### Prerequisites

To run the program, a sbt shell is needed. Intellij has all premise installation requirements.

### Logic
When consumer receives a ZendeskDomain object it fires following operations;
1. Performs an initial call to Zendesk's time-based endpoint to fetch all tickets opened until start time.
2. System preserves the cursor information to fetch the next set of tickets in each run.
3. With the initial cursor info, a throttled infinite stream starts to fetch tickets.
4. In each run tickets are displayed in console.
### Sample Call

Request
```
curl -d '{"domain":{domain},"oauth":{token},"startTime":"1511111871"}' -H "Content-Type: application/json" -X POST http://localhost:3000/data
```

Response
```
Ok(200)
```

## Built With

* [SBT](https://maven.apache.org/) - Dependency Management


## Versioning

v0.1

## Authors

* **Soner Guzeloglu** - *Initial work*



