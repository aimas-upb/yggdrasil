# Yggdrasil

A platform for [Hypermedia Multi-Agent Systems (MAS)](https://hyperagents.org/) [1] built with
[Vert.x](https://vertx.io/). 
This implementation is a fork of the original Hypermedia MAS (HMAS) platform called [Yggdrasil](https://github.com/Interactions-HSG/yggdrasil).
The upstream implementation provides two core functionalities:
* it allows to program and deploy hypermedia environments for autonomous agents that conform to the
  _Agents & Artifacts_ meta-model [2]
* it partially implements the [W3C WebSub recommendation](https://www.w3.org/TR/2018/REC-websub-20180123/)
  and can act as a WebSub hub

# Yggdrasil and CASHMERE
The original functionalities are extended in the current fork with support for (see also [3] and [4]):
* Management of *context information* leveraged in a deployed HMAS through a ContextManagement Service that can:
  - Keep track of *static* and *profiled* context information. Context is represented as RDF graphs following the *ContextAssertion* model [5]
  - Keep track of *dynamic* context streams, referencing information that can change frequently (e.g. activities, location). Content of context streams is represented as *ContextAssertions*.
* Management of *ContextDomains* [4,6] and *ContextDomainGroups* as facilitators for identification of **shared dynamic context** between an agent and a web resource.
* Generation of **authorizations** for *context-based access* to the affordances of a web resource using the policy format specified by [SOLID Web Access Control](https://solid.github.io/web-access-control-spec/)
* Management of interactions between a requesting agent and the affordances of a *context-based access controlled* web resource

These additions are part of the [CASHMERE project](https://sites.google.com/view/cashmere-project/) which researches mechanisms to provide *context-based* authorization, search and discovery functionality for agents interacting with web resources in a Hypermedia MAS.

#### References

[1] Andrei Ciortea, Olivier Boissier, Alessandro Ricci. 2019. Engineering World-Wide Multi-Agent Systems
with Hypermedia. In: Weyns D., Mascardi V., Ricci A. (eds) Engineering Multi-Agent Systems. EMAS 2018.
Lecture Notes in Computer Science, vol 11375. Springer, Cham. https://doi.org/10.1007/978-3-030-25693-7_15

[2] Alessandro Ricci, Michele Piunti, and Mirko Viroli. 2011. Environment Programming in multi-agent
systems: an artifact-based perspective. Autonomous Agents and Multi-Agent Systems, 23(2):158-192.

[3] Sorici, A. and Florea, A.M., 2023, May. Towards Context-Based Authorizations for Interactions in Hypermedia-Driven Agent Environments-The CASHMERE Framework. In International Workshop on Engineering Multi-Agent Systems (pp. 191-207). Cham: Springer Nature Switzerland.

[4] Sorici, A. and Florea, A.M. 2024. Towards Enabling Context-Based Dynamic Access Control in Hypermedia MAS - a Technical Report. [link](https://tinyurl.com/cashmere-context-based-auth)

[5] Sorici, A., Picard, G., Boissier, O., Zimmermann, A. and Florea, A., 2015. CONSERT: Applying semantic web technologies to context modeling in ambient intelligence. Computers & Electrical Engineering, 44, pp.280-306.

[6] Sorici, A., Picard, G., Boissier, O. and Florea, A., 2015. Multi-agent based flexible deployment of context management in ambient intelligence applications. In Advances in Practical Applications of Agents, Multi-Agent Systems, and Sustainability: The PAAMS Collection: 13th International Conference, PAAMS 2015, Salamanca, Spain, June 3-4, 2015, Proceedings 13 (pp. 225-239). Springer International Publishing.



## Prerequisites for running the updated Yggdrasil Platform

* JDK 12+
* Use `git clone --recursive` to make sure that the project is checked out including its submodules

## Building the project

To build the project, just use:

```shell
./gradlew
```

The default Gradle task `shadowJar` generates a fat-jar in the `build/libs` directory.


## Running Yggdrasil

To start an Yggdrasil node:

```shell
java -jar build/libs/yggdrasil-0.0-SNAPSHOT-fat.jar -conf src/main/conf/config.json
```

The configuration file is optional. Open your browser to
[http://localhost:8080](http://localhost:8080). You should see an `Yggdrasil v0.0` message.

## Running Yggdrasil as a Docker container

Build the image with the current context and creates the image `yggdrasil`:

```shell
docker-compose build
```

Run with docker-compose (by default, it exposes the port `8899` of the host machine):

```shell
docker-compose up
```

## HTTP API Overview for Yggdrasil

The HTTP API implements CRUD operations for 3 types of resources:

* environments (URI template: `/environments/<env_id>`)
* workspaces (URI template: `/workspaces/<wksp_id>`)
* artifacts (URI template: `/artifacts/<art_id>`)

`POST` and `PUT` requests use [Turtle](http://www.w3.org/TR/2014/REC-turtle-20140225/) payloads
and the current implementation only validates the payload's syntax.

`POST` requests can use the `Slug` header (see [RFC 5023](https://tools.ietf.org/html/rfc5023#section-9.7))
to hint at a preferred IRI for a resource to be created. If the IRI is not already in use, it will
be minted to the created resource.

When creating a resource via `POST`, the resource to be created is identified in the Turtle payload
via a null relative IRI:

```shell
curl -i -X POST \
  http://localhost:8080/environments/ \
  -H 'content-type: text/turtle' \
  -H 'slug: env1' \
  -d '<> a <http://w3id.org/eve#Environment> ;
<http://w3id.org/eve#contains> <http://localhost:8080/workspaces/wksp1> .'
```

When retrieving the representation of a resource from Yggdrasil, the HTTP response contains 2 `Link`
header fields that advertise a WebSub hub that clients can subscribe to in order to receive
notifications whenever the resource is updated (see the
[W3C WebSub recommendation](https://www.w3.org/TR/2018/REC-websub-20180123/)).
Sample request:

```shell
GET /workspaces/wksp1 HTTP/1.1
Host: yggdrasil.andreiciortea.ro

HTTP/1.1 200 OK
Content-Type: text/turtle
Link: <http://yggdrasil.andreiciortea.ro/hub>; rel="hub"
Link: <http://yggdrasil.andreiciortea.ro/workspaces/wksp1>; rel="self"

<http://yggdrasil.andreiciortea.ro/workspaces/wksp1>
  a <http://w3id.org/eve#Workspace> ;
  <http://w3id.org/eve#hasName> "wksp1" ;
  <http://w3id.org/eve#contains>
    <http://85.204.10.233:8080/artifacts/hue1> ,
    <http://yggdrasil.andreiciortea.ro/artifacts/event-gen> .
```

Using the discovered hub and topic IRIs, a client can subscribe for notification via a `POST` request
that contains a JSON payload with the following fields (see the
[W3C WebSub recommendation](https://www.w3.org/TR/2018/REC-websub-20180123/)):

 * `hub.mode`
 * `hub.topic`
 * `hub.callback`

When a resource is updated, Yggdrasil issues `POST` requests with the (updated) resource
representation to all registered callbacks.

## HTTP API Overview for CASHMERE extension
The Context Management Service added to Yggdrasil will return information on the managed context streams, ContextDomains and ContextDomainGroups by means of the following URI templates:
* Context streams (URI template: `/environments/<env_id>/ctxmgmt/streams`)
* ContextDomains (URI template: `/environments/<env_id>/ctxmgmt/domains`)
* ContextDomainGroups (URI template: `/environments/<env_id>/ctxmgmt/domains/<domain_name>/group`)

