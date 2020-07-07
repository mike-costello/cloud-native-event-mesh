# ***Cloud Native Integration***: *A working* ***Demo*** 

This repository contains a working demonstration of how to quickly implement a Cloud Native architecture approach (with Enterprise Integration patterns as a central concern) as distilled in [Cloud Native Integration] (https://github.com/rh-ei-stp/cloud-native-integration). 

## Demo Description 

As we distill the high level system architecture as described by the *Cloud Native Integration* document ![Cloud Native Integration: The View From Space](/images/CloudNativeIntegration.00.00.001.png "Cloud Native Integration: From Space")

We notice a few distinct architectural layers: 
* **Event Mesh**
The *event mesh* intends to handle peer to peer event communication in a fashion that allows for several *Cloud Native* characteristics such as high availability, reliability, and location agnostic behaviour between peers. The event mesh acts as a rendezvous point between eventing peers (event emitters and event receivers), and provides event emitters a graph of event receivers that may span clusters or even clouds. 

* **Event Sink**
The *event sink* provides a port to the underlying event bus our integrations process events on. 

* **Event Bus**
The event bus provides a service bus so that event processors, that often need to integrate multiple different data sources to provide event aggregate level output, are able to communicate with each other in an asynchronous fashion. This decouples event emitters and receivers, as well as decoupling event aggregate behaviour that distills our series of events into meaningful business level data. As a result, integrations are bound to their domain, and are likely decomposed along their bounded context. It is along the *event bus* that features that constitute our enterprise perform the stream processing and integration work that satisfies enterprise features. Inevitably, these stream processors and integration components aggregate events into sources of truth to maintain consistency and state across the enterprise.   

* **Event Store** 
The event store represents a persistent source of truth for events and event aggregates. We define *aggregate event* as events that attempt to provide consistency boundaries for transactions, distributions and concurrency. In our view, while events may be represented in a transitive store such as Kafka, event aggregates define consistency across our data plane and represent a conceptual whole for state at a point in time for our domain entitties. As a result, our event store should be viewed as a conceptual whole that can offer capabilities as a source of truth for volatile, short term events, and a source of truth for long standing event aggregates.  

### Representing these Ports, Adapters and Layers

This demo seeks to display these architectural techniques by leveraging the Red Hat Integration platform. 

* **Event Mesh** 
To provide *event mesh* capabilities, this demo leverages *Apache Qpid Dispatch Router* to ensure a service and event communication control plane that provides capabilities for *high availability, resilience, multi and hybrid cloud* as well as policy to apply over the mesh to ensure governance is properly applied as our event emitters and event receivers communicate in a peer to peer fashion 

* **Event Sink** 
To provide an *event sink* as a port into our underlying business logic and sources of truth, we leverage *Apache Camel* and a set of complementary cloud native tooling

* **Event Bus** 
The event bus provides a normalized means of asynchronous behaviour for event consumers and emmitters. As *Cloud Native Integration* depends on and features cloud native capabilities, we leverage *Knative Eventing* to provide a cloud native service bus abstraction with *Apache Kafka* as the underlying persistence engine for our communication over our service bus channels. 

* **Event Store** 
As *Apache Kafka* is the persistence store for our volatile events and event aggregates that travel along our service bus, this demonstration relies on a traditional OLTP store as the inevitable source of truth for event aggregates. While an OLTP store is not required as a source of truth for event aggregates in our view of the world, it describes the complementary nature of *Cloud Native Integration* to traditional legacy enterprise deployments. 

## Getting Started 

*Quick Note: This demonstration uses Openshift 4.x which relies on Kubernetes 1.16 and above. As a result, despite the use of Operator Hub and Red Hat disitributed Operators, the steps outlined in this document may be followed in any Kubernetes distro that is 1.16 or higher and a matching community version of the operators being deployed* 

### Installing Operators 

From the Openshift Operator Hub, install the following operators (in our case, we'll install these operators with *cluster admin* credentials, and will allow these operators to observe all namespaces). 
* Red Hat Integration - AMQ Streams 
* Red Hat Integration - AMQ Certificate Manager 
* Red Hat Integration - AMQ Interconnect (this operator will need to be installed in multiple namespaces due to its limitation of watching a single K8s namespace)
* Openshift Serverless Operator 
* Camel K Operator 

We should now find Operators running in the OpenshiftOperators namespace: 
![OpenshiftOperators](/images/openshift-operator-ns-initial.png)

### Preparing For Deployment 

At this point, we have installed the required operators; however, our environment still isn't ready to start laying down deployments. 

#### Install Knative Serving 
To install Knative Serving which provides our serverless framework by the OpenshiftServerless Operator, there are a few more steps. 

* Create the knative-serving namespace with a user that posseses the cluster-admin role: 

```
oc create namespace knative-serving
```
* Apply the knative serving yaml to the cluster as described in the following file ![Knative Serving](/src/main/k8s/knative-serving/knative-serving-cr.yaml)

```
apiVersion: operator.knative.dev/v1alpha1
kind: KnativeServing
metadata:
    name: knative-serving
    namespace: knative-serving
``` 

Upon succesfful installation, there should be a similar result to the following: 

```
oc get pods -w -n knative-serving 
NAME                                READY     STATUS    RESTARTS   AGE
activator-7db4dc788c-spsxb          1/1       Running   0          1m
activator-7db4dc788c-zqnnd          1/1       Running   0          45s
autoscaler-659dc48d89-swtmn         1/1       Running   0          59s
autoscaler-hpa-57fdfbb45c-hsd54     1/1       Running   0          49s
autoscaler-hpa-57fdfbb45c-nqz5c     1/1       Running   0          49s
controller-856b4bd96d-29xlv         1/1       Running   0          54s
controller-856b4bd96d-4c7nn         1/1       Running   0          46s
kn-cli-downloads-7558874f44-qdr99   1/1       Running   0          1m
webhook-7d9644cb4-8xkzt             1/1       Running   0          57s
```

#### Install Knative Eventing 
Knative Eventing provides functionality around our Cloud Event abstraction and forms the operational basis of our cloud native service bus. 

To install Knative Eventing we need to perform the following steps (with a cluster admin user): 
* Create the knative-eventing namespace 

```
oc create namespace knative-eventing
```

Upon creation of the namespace, we will want to create the knative eventing operators by applying the following cr ![Knative Eventing CR](/src/main/k8s/knative-eventing/knative-eventing-cr.yaml)

In our case, as we'll have need later, we'll install the Multi Tenant Channel Based Broker as our default Knative Eventing Broker implementation: 

```
apiVersion: operator.knative.dev/v1alpha1
kind: KnativeEventing
metadata:
  name: knative-eventing
  namespace: knative-eventing
spec:
  defaultBrokerClass: MTChannelBasedBroker
```

Upon applying this yaml, something similar to this should be true: 

```
oc get pods -w -n knative-eventing 
NAME                                   READY     STATUS    RESTARTS   AGE
broker-controller-67b56668bd-sgxwg     1/1       Running   0          1m
eventing-controller-544dc9945d-pz2cl   1/1       Running   0          1m
eventing-webhook-6c774678b5-lzfkn      1/1       Running   0          1m
imc-controller-78b8566465-smpb7        1/1       Running   0          1m
imc-dispatcher-57869b44c5-s7t92        1/1       Running   0          1m
```

At this point, we have installed the knative eventing controllers, dispatchers and webhooks; however, we only have support for the in memory channel, which means we will not be able to use a persistent approach to knative eventing brokers and their respective channels in our cluster. 

##### Installing Kafka Channels
In our above *Cloud Native Integration* schematic, what lies at the heart of our event bus is *Apache Kafka* so for knative eventing to follow this architectural construct, we need our broker channels to be persisted by Kafka. While this component is not generally available, it has reached a considerable maturity level and relies largely on its surrounding ecosystem that is generally available. 

In this demonstration, we will deploy a KafkaChannel deployment that creates a set of CR's, controllers for our channels, requisite service accounts, and webhooks to instrument creation, removal and discovery of KafkaChannels that may be associated with a Knative eventing Broker. 

By issuing the following command (this assumes cluster admin permissions for the user issuing commands and that the previous knative-eventing steps have taken place): 

``` 
oc apply -f ./src/main/install/kafka-channel/kafka-channel-install.yaml
clusterrole.rbac.authorization.k8s.io/kafka-addressable-resolver created
clusterrole.rbac.authorization.k8s.io/kafka-channelable-manipulator created
clusterrole.rbac.authorization.k8s.io/kafka-ch-controller created
serviceaccount/kafka-ch-controller created
clusterrole.rbac.authorization.k8s.io/kafka-ch-dispatcher created
serviceaccount/kafka-ch-dispatcher created
clusterrole.rbac.authorization.k8s.io/kafka-webhook created
serviceaccount/kafka-webhook created
clusterrolebinding.rbac.authorization.k8s.io/kafka-ch-controller created
clusterrolebinding.rbac.authorization.k8s.io/kafka-ch-dispatcher created
clusterrolebinding.rbac.authorization.k8s.io/kafka-webhook created
customresourcedefinition.apiextensions.k8s.io/kafkachannels.messaging.knative.dev created
configmap/config-kafka created
configmap/config-leader-election-kafka created
service/kafka-webhook created
deployment.apps/kafka-ch-controller created
mutatingwebhookconfiguration.admissionregistration.k8s.io/defaulting.webhook.kafka.messaging.knative.dev created
validatingwebhookconfiguration.admissionregistration.k8s.io/validation.webhook.kafka.messaging.knative.dev created
secret/messaging-webhook-certs created
deployment.apps/kafka-webhook created

```

We should now see new contoller, dispatcher, and webhook pods in our knative-eventing namespace: 

```
NAME                                   READY     STATUS    RESTARTS   AGE
broker-controller-67b56668bd-sgxwg     1/1       Running   0          1h
eventing-controller-544dc9945d-pz2cl   1/1       Running   0          1h
eventing-webhook-6c774678b5-lzfkn      1/1       Running   0          1h
imc-controller-78b8566465-smpb7        1/1       Running   0          1h
imc-dispatcher-57869b44c5-s7t92        1/1       Running   0          1h
kafka-ch-controller-7f88b8c776-crhm4   1/1       Running   0          1m
kafka-webhook-b47dc9767-hmnm4          1/1       Running   0          1m
```

We should also now have a resource that describes resources that will inevitably describe how channels are bound to Kafka topics in our Knative Eventing framework. 

```
oc api-resources | grep messaging.knative.dev
channels                              ch               messaging.knative.dev                 true         Channel
inmemorychannels                      imc              messaging.knative.dev                 true         InMemoryChannel
kafkachannels                         kc               messaging.knative.dev                 true         KafkaChannel
subscriptions                         sub              messaging.knative.dev                 true         Subscription
```

If we have gotten this far we have most of our infrastructure assembled from an operator perspective and now its time to being installing our concrete implementation. 

## Installing the Demo 

At this point our operators are installed and we have mostly configured our environment; however, we still have a few house keeping tasks to take care of: 
* We need to create and install a trust store so that we may communicate to and between clusters in a secure fashion 
* We need to ensure proper trust and authority is distributed to the correct places in our cluster 

### Using the AMQ Certificate Manager Operator 

We will use the [cert-manager](https://cert-manager.io) operator that we've previously provisioned to issue certificates that we'll need to wire secure connections across the cluster. 

#### Creating a CA to use across the cluster 

For our puroposes we will create a Certificate Authority using OpenSSL. Initially, the following command should be issued to generate the private key for our CA

```
openssl  genrsa -des3 -out cloudEventMeshDemoCA.key 2048
```
OpenSSL will prompt for a passphrase. It is recommended to use a passphrase even in a development environment, and especially when cluster resources may be accessible from outside of the cluster. 

We'll use the key to create a root certificate that will act as our certificate authority: 

```
openssl req -x509 -new -nodes -key cloudEventMeshDemoCA.key -sha256 -days 1825 -out cloudEventMeshDemoCA.crt
```

At this point, you will be asked for a passphrase for again, and as always, it is apropos to use one and not skip this step. While creating this CA OpenSSL will ask for OU's, DN's, etc., and it may be important to use meaningful values for these as it may be required during later configuration. 

Upon completing this process, we will now have an available CA to sign with, and we'll use the AMQ distribution of the certificate manager to establish our CA as a certificate issuer across the cluster. 

For convenience sake, we have included a secret in this demo that we should apply to where our certificate manager operator lives (in our case the project *openshift-operators*)

```
oc apply -f ./src/main/k8s/CA/cloud/cloud-native-event-mesh-ca-secret.yaml
```
This could also be accomplished by creating a secret from the the CA private and public key created above. 

Now that we have created our CA keypair secret, let's create a certificate manager issuer that uses our CA: 

```
apiVersion: certmanager.k8s.io/v1alpha1
kind: ClusterIssuer
metadata:
  name: cloud-native-event-mesh-demo-cert-issuer
spec:
  ca:
    secretName: cloud-native-event-mesh-demo-ca-pair

```

Upon completing our application of the clusterissuer resource we should have a cluster issuer. 

```py
oc describe clusterissuer cloud-native-event-mesh-demo-cert-issuer 
Name:         cloud-native-event-mesh-demo-cert-issuer
Namespace:    
Labels:       <none>
Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"certmanager.k8s.io/v1alpha1","kind":"ClusterIssuer","metadata":{"annotations":{},"name":"cloud-native-event-mesh-demo-cert-issuer","name...
API Version:  certmanager.k8s.io/v1alpha1
Kind:         ClusterIssuer
Metadata:
  Creation Timestamp:  2020-06-29T17:11:47Z
  Generation:          2
  Resource Version:    1485153
  Self Link:           /apis/certmanager.k8s.io/v1alpha1/clusterissuers/cloud-native-event-mesh-demo-cert-issuer
  UID:                 90035a64-67c1-4440-9526-a87d1297bfa2
Spec:
  Ca:
    Secret Name:  test-key-pair
Status:
  Conditions:
    Last Transition Time:  2020-06-29T17:11:47Z
    Message:               Signing CA verified
    Reason:                KeyPairVerified
    Status:                True
    Type:                  Ready
Events:
  Type    Reason           Age              From          Message
  ----    ------           ----             ----          -------
  Normal  KeyPairVerified  8s (x2 over 8s)  cert-manager  Signing CA verified
```

We are now free to issue certificates in our cluster. This will be critical to setting up our next steps, the event mesh. 

### Installing the Event Mesh 

Our event mesh will span three different namespaces in our cluster; however, our intention is to logically represent three seperate clusters in our topology. As a result, we will need to create trust in each of these namespaces for the other routers in our event mesh, as, we would not allow insecure communication from cluster to cluster. 

#### Creating the namespaces 

At this point we will wand to create the following namespaces: 
* *cluster-1* 
* *cluster-2* 
* *edge* - this will represent an edge cluster in our topology. While some topologies may not call for an edge cluster, we still want to ensure that we use an *edge router* somewhere in our openshift cluster to ensure that we have connection concentration, a single source of policy application to incoming requests from outside of the cluster, as well as a terminal leaf node for our event mesh graph. 

##### Installing the Interconnect Router in *cluster-1*

As we have use of the operator hub in Openshift 4, we will simply install an Interconnect Operator to the namespace "cluster-1". 
![Installing the Interconnect Operator in Cluster 1](/images/interconnect-cluster-1.png "Installing the Interconnect Operator to Cluster-1")

At this point, we will be able to start creating some certificates from the cluster issuer we have already established and inevitably configure our Interconnect router for trust. 
In the namespace "cluster-1", we will provision a certificate for our Interconnect router which we will use to wire up the Interconnect router for trust across inter-router connections. 

*Please Note*: The interconnect router has self-signed a certificate using the certificate manager, and the demonstrated use of certificate management here is only applicable to inter-router conncetions

Initially, we'll lay down the certificate request custom resource: 

```py
oc apply -f ./src/main/k8s/cloud-1/router/cloud1-certificate-request.yaml
```
This certificate request: 

```yaml
apiVersion: certmanager.k8s.io/v1alpha1
kind: Certificate
metadata:
  name: cluster-wide-tls
spec:
  secretName: cluster-wide-tls
  commonName: openshift.com
  issuerRef:
    name: cloud-native-event-mesh-demo-cert-issuer
    kind: ClusterIssuer
  dnsNames: 
     - openshift.com
     - eipractice.com
     - opentlc.com

```

Will leverage the certificate manager to create a secret in the cluter referred to as *"cluster-wide-tls"* which holds the CA certificate, private key, and other things to ensure trust as issued by the ClusterIssuer we have created above. 

Upon issuing the certificate, it is now time to apply our Interconnect router custom resource for the cluster-1 namespace: 

```
oc apply -f ./src/main/k8s/cloud-1/router/cloud1-mesh-router.yaml 
```

This enables a few features as laid out by the Interconnect custom resource: 

```
apiVersion: interconnectedcloud.github.io/v1alpha1
kind: Interconnect
metadata:
  name: cloud1-router-mesh
spec:
   sslProfiles:
   - name: inter-router
     credentials: cluster-wide-tls
     caCert: cluster-wide-tls
   deploymentPlan: 
      role: interior 
      size: 1
      placement: AntiAffinity
   interRouterListener: 
      sslProfile: cloud1-router-tls
      expose: false
      authenticatePeer: false
      port: 55671
```

This creates an SSL Profile based on our CA certificate as issued to us by the cluster issuer as well as establishes an interRouterListener backed by this SSL Profile. The router will also configure other things by default, and with self-signed security, such as: 
* A default AMQP listener secured by a *self-signed* certificate
* Metrics and management capabilities 
* A means for Prometheus or other ***AMP*** technologies to observe the event mesh router as well as the link attachments that event peers make over the event mesh 

At this point, in cluster-1 we will have both an event mesh router and the Interconnect operator in our *"cluster-1"* namespace: 

```
[mcostell@work router]$ oc get pods -w -n cluster-1
NAME                                    READY     STATUS    RESTARTS   AGE
cloud1-router-mesh-7f698d8c65-wpnx7     1/1       Running   0          13m
interconnect-operator-56b7884d4-6j4jl   1/1       Running   0          5h

 oc get interconnects 
NAME                 AGE
cloud1-router-mesh   17m
```
We can also introspect the router via oc exec commands: 

```
[mcostell@work router]$ oc exec cloud1-router-mesh-7f698d8c65-wpnx7 -i -t -- qdstat -g
2020-06-29 23:23:49.882216 UTC
cloud1-router-mesh-7f698d8c65-wpnx7

Router Statistics
  attr                             value
  ========================================================================================
  Version                          Red Hat AMQ Interconnect 1.8.0 (qpid-dispatch 1.12.0)
  Mode                             interior
  Router Id                        cloud1-router-mesh-7f698d8c65-wpnx7
  Worker Threads                   4
  Uptime                           000:00:22:17
  VmSize                           497 MiB
  Area                             0
  Link Routes                      0
  Auto Links                       0
  Links                            2
  Nodes                            0
  Addresses                        11
  Connections                      1
  Presettled Count                 0
  Dropped Presettled Count         0
  Accepted Count                   24
  Rejected Count                   0
  Released Count                   0
  Modified Count                   0
  Deliveries Delayed > 1sec        0
  Deliveries Delayed > 10sec       0
  Deliveries Stuck > 10sec         0
  Deliveries to Fallback           0
  Links Blocked                    0
  Ingress Count                    24
  Egress Count                     23
  Transit Count                    0
  Deliveries from Route Container  0
  Deliveries to Route Container    0

```
##### Installing the Interconnect Router in *cluster-2*
Now that we have an event mesh router in *cluster-1*, we will link routers together to form an event mesh between *cluster-1* and *cluster-2*. 
*Please Note* - the intention of this logical delineation is to represent 2 seperate clusters. In practice, *interior event mesh* routers would bind remote clusters together 

The process of enabling inter-router connections between event mesh routers in namespaces *cluster-1* and *cluster-2* is similar to the provisioning that was required for the *cluster-1* event mesh router. 

Initially, we want to install the *Red Hat - Interconnect Operator* in the namespace *cluster-2*. As *cluster-2* will also issue its certificates from the *cluster-issuer* provisioned previously in the demo, upon installation of the Interconnect Operator, we will provision a CA certificate for *cluster-2* via a certificate request from the *ClusterIssuer*. 

```py 
oc apply -f src/main/k8s/cloud-2/router/cloud2-certificate-request.yaml
```

This certificate request: 

```py 
apiVersion: certmanager.k8s.io/v1alpha1
kind: Certificate
metadata:
  name: cluster-wide-tls
spec:
  secretName: cluster-wide-tls
  commonName: openshift.com
  issuerRef:
    name: cloud-native-event-mesh-demo-cert-issuer
    kind: ClusterIssuer
  dnsNames: 
     - openshift.com
     - eipractice.com
     - opentlc.com
```

Will again provision our CA into a secret into the cluster named "cluster-wide-tls". 

Upon the certificate manager cluster issuer issuing a CA into *cluster-2*, we can lay down the Interconnect resource that will enable our *cluster-1* and *cluster-2* event meshing. 

```py
oc apply -f src/main/k8s/cloud-2/router/cloud2-certificate-request.yaml
```
The router provisioned takes mostly default values for the Interconnect configuration; however, does create an interior router connection to the event mesh router in *cluster-1*: 

```py 
apiVersion: interconnectedcloud.github.io/v1alpha1
kind: Interconnect
metadata:
  name: cloud2-router-mesh
spec:
  sslProfiles:
  - name: inter-cluster-tls
    credentials: cluster-wide-tls
    caCert: cluster-wide-tls
  interRouterConnectors:
  - host: cloud1-router-mesh.cluster-1.svc
    port: 55671
    verifyHostname: false
    sslProfile: inter-cluster-tls
```

Upon succesful provisioning of the router in *cluster-2* we should be able to see a successfull interior router connection between the routers in *cluster-1* and *cluster-2*:

```py 
oc exec cloud2-router-mesh-d566476ff-msdrr -i -t -- qdstat -c 
2020-06-29 23:37:04.652856 UTC
cloud2-router-mesh-d566476ff-msdrr

Connections
  id  host                                    container                             role          dir  security                                authentication  tenant  last dlv      uptime
  =================================================================================================================================================================================================
  2   cloud1-router-mesh.cluster-1.svc:55671  cloud1-router-mesh-7f698d8c65-wpnx7   inter-router  out  TLSv1/SSLv3(DHE-RSA-AES256-GCM-SHA384)  x.509                   000:00:00:00  000:00:00:54
  9   127.0.0.1:33562                         ae807937-90d0-44d7-8f7b-afdc14f5c47a  normal        in   no-security                             no-auth                 000:00:00:00  000:00:00:00
```

##### Installing the Edge Interconnect Router
At this point we have a mesh of Interconnect routers in *cluster-1* and *cluster-2*; however, to properly be able to scale our router network and provide a connection concentrator for our eventing applications, we will want to establish a member of our event mesh to have the role of "edge" in our cluster. Edge routers act as connection concentrators for messaging applications. Each edge router maintains a single uplink connection to an interior router, and messaging applications connect to the edge routers to send and receive messages.

Initially, we will to create a namespace named ***"edge"***. ***Please Note***: in practice, our edge router would likely live in a seperate cluster meant to handle edge use cases, and would be seperate from *cluster-1* and *cluster-2*. 

Upon succesfull creation of the namespace, it is neccessarry to install the Interconnect Operator into this namespace. This will allow us to provision our *edge* router resource. 

Initially, as with our other logical *clusters* we will provision our edge namespace with a CA certificate: 

```py
oc apply -f src/main/k8s/edge/edge-certificate-request.yaml
```

Upon creating our *cluster-wide-tls* secret in the namespace, we'll provision our Interconnect Router which will have uplinks to both *cluster-1* and *cluster-2*: 

```py 
oc apply -f src/main/k8s/edge/edge-routers.yaml
```

This will provision our *edge* event mesh router, to perform a role as a terminal node in our event mesh. 

```py
apiVersion: interconnectedcloud.github.io/v1alpha1
kind: Interconnect
metadata:
  name: edge-routers
spec:
  sslProfiles:
   - name: edge-router-tls
     credentials: cluster-wide-tls
     caCert: cluster-wide-tls
  deploymentPlan:
    role: edge
    placement: AntiAffinity
  edgeConnectors:  
    - host: cloud1-router-mesh.cloud-1
      port: 45672
      name: cloud1-edge-connector
    - host: cloud2-router-mesh.cloud-2
      port: 45672
      name: cloud2-edge-connector
  listeners: 
    - sslProfile: cluster-wide-tls
      authenticatePeer: false
      expose: true
      http: false
      port: 5671
```

If we were to peruse the Interconnect console for one of our clusters, we would be able to see our two interior routers fronted by a single edge router: 
![Initial Event Mesh Topology](/images/event-mesh-initial-topology.png)

###Installing the Event Bus

***Cloud Native Integration*** creates an event mesh through which event emitters and receivers are able to negotiate with each other, get guarantees around delivery, and ensure proper communication flow via Interconnects wire level flow control capabilities. 

While the *event mesh* serves to provide a communications control plane for event emitters and receivers, through which event level qos can be applied to its relevant use cases, and a reliable graph of receivers for emitted events, ***Cloud Native Integration*** introduces a complementary persistent event bus, as an event sink for event stream processing and integration from the event mesh. This event bus provides a persistent source of *truth* for event stream processors, and allows event receivers to take advantage of platform capabilities such as elasticity, contractual communication, and extend those capabilities into serverless capabilities such as *scaling to zero* and other highly elastic behaviour. 

To accomplish this ***Cloud Native Integration*** relies on *Knative Eventing*, and *Knative Serving* from the *Openshift Serverless* Operator that we deployed earlier. 

As "Knative Eventing" proposes a pub-sub architecture with per namespace Brokers and subscriptions as described by:
![Knative Broker Triggers](/images/broker-trigger-overview.svg)
 
It will be neccessarry to provision a persistent source of truth for our Knative event brokers. ***Cloud Native Integration*** proposes the use of AMQ Streams as this persistent source of truth as it offers two features that uniquely assist in our pub-sub abstraction: 
* A co-ordinated distributed log that replicates across distinct physical parts of an Openshift cluster 
* A means of providing distributed log partitions to ephemeral consumer groups via the Knative channels abstraction and a Kafka Topic implementation 

As the *Integrations* that handle our events consume from our underlying event bus via Knative subscriptions to Knative event channels, AMQ Streams provides a *cloud native* means to persist these events: 
![Knative Broker Channels](/images/control-plane.png)

#### Provisioning AMQ Streams

For our use case in a single cluster, we will simply provision a single AMQ Streams cluster for both *cluster-1* and *cluster-2* event bus consumers; however, in practice, it would be apropos to at minimum extend the mult-tenant features of this demo to ensure the use of distinct/multi-tenant physical Kafka clusters. 

As we have already installed the AMQ Streams Operator cluster wide, we will simply create a namespace for our AMQ Streams cluster to live in. Let's call it "amq-streams". 

```py 
oc create namespace amq-streams
```

Upon succesful creation of the namespace, we will apply our AMQ Streams custom resource to create an AMQ Streams cluster in the namespace called *"small-event-cluster"*: 

```py
oc apply -f src/main/k8s/cloud-1/kafka/small-event-cluster.yaml
```

Upon succesfful creation of our resources, we should see our *"amq-streams"* cluster with some new pods: 

```py
oc get pods -w -n amq-streams 
NAME                                                   READY     STATUS    RESTARTS   AGE
small-event-cluster-entity-operator-7cb59948f7-r22kp   3/3       Running   0          33s
small-event-cluster-kafka-0                            2/2       Running   1          1m
small-event-cluster-zookeeper-0                        1/1       Running   0          2m
small-event-cluster-zookeeper-1                        1/1       Running   0          2m
small-event-cluster-zookeeper-2                        1/1       Running   0          2m
```

At this point we have provisioned AMQ Streams but we need to install Knative Eventing and Knative Serving so that AMQ Streams is the backbone of our enterprise service bus abstraction. 

#### Provisioning Kafka Channels using the Knative Eventing Broker  
As we have already installed the *Openshift Serverless Operator* in a previous step, we are now free to leverage the *Kafka Channel* configuration we created earlier to provision our Knative evening abstraction *channel* as a Kafka topic. 

Let's initially ensure we have configured our knative eventing operator's webhooks to wire up for default behaviour  by applying several configmaps: 

```py
oc apply -f ./src/main/k8s/config-maps/knative-eventing/ -n knative-eventing 
```

This will wire up our broker and channel controllers to have a default broker configuration: 

```py
kind: ConfigMap
apiVersion: v1
metadata:
  name: config-br-defaults
  namespace: knative-eventing
data:
  default-br-config: |
    clusterDefault:
      brokerClass: MTChannelBasedBroker
      apiVersion: v1
      kind: ConfigMap
      name: kafka-channel
      namespace: knative-eventing
    namespaceDefaults:
      test-tenant:
        brokerClass: MTChannelBasedBroker
        apiVersion: v1
        kind: ConfigMap
        name: imc-channel
        namespace: knative-eventing
```

At this point, we will label our respective *cluster-1* and *cluster-2* namespaces for injection by our *Knative Eventing Operator*. 

```py 
oc label namespace cluster-1 knative-eventing-injection=enabled 
oc label namespace cluster-2 knative-eventing-inection=enabled 
Initially, in the namespace *cluster-1* we'll provision Knative eventing *Channel* custom resources: 
```
Upon labeling these namespaces, we should see broker knative-eventing broker pods in our namespace (for instance in namespace *cluster-1*): 

```py 
oc get pods -w -n cluster-1
NAME                                      READY     STATUS    RESTARTS   AGE
cloud1-router-mesh-7f698d8c65-xft27       1/1       Running   0          1h
default-broker-filter-b5967fd6c-rs8vm     1/1       Running   0          13s
default-broker-ingress-6d4c6474c8-9gd4m   1/1       Running   0          13s
interconnect-operator-5684994fbf-587dk    1/1       Running   0          1h
```

We should also be able to interrogate our ***AMQ Streams*** broker and see that we now have topics for our trigger based subscriptions in our default brokers in namespaces that have been injected: 

```py
oc exec small-event-cluster-kafka-0 -c kafka -it -- bin/kafka-topics.sh --zookeeper localhost:2181 --list 
OpenJDK 64-Bit Server VM warning: If the number of processors is expected to increase from one, then you should configure the number of parallel GC threads appropriately using -XX:ParallelGCThreads=N
knative-messaging-kafka.cluster-1.default-kne-trigger
knative-messaging-kafka.cluster-2.default-kne-trigger
```

At this point, our default brokers in namespace are wired for use of a default channel, a *Kafka Channel*, and we can begin wiring up our knative brokers for the channels we would like to interact with: 

Let's start by creating a channel in *cluster-1* that we will use during our demo: 

```py
oc apply -f ./src/main/k8s/cloud-1/eventing/testing-dbevents-channel.yaml -n cluster-1
```

If we interrogate K8s about this resource, we'll see that our *testing-dbevents channel* is in fact a *kafka channel*: 

```py
oc describe channels testing-dbevents 
Name:         testing-dbevents
Namespace:    cluster-1
Labels:       <none>
Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"messaging.knative.dev/v1beta1","kind":"Channel","metadata":{"annotations":{},"name":"testing-dbevents","namespace":"cluster-1"}}

              messaging.knative.dev/creator=opentlc-mgr
              messaging.knative.dev/lastModifier=opentlc-mgr
API Version:  messaging.knative.dev/v1beta1
Kind:         Channel
Metadata:
  Creation Timestamp:  2020-07-07T02:00:56Z
  Generation:          1
  Resource Version:    1628649
  Self Link:           /apis/messaging.knative.dev/v1beta1/namespaces/cluster-1/channels/testing-dbevents
  UID:                 c0f05d96-5e8d-46b5-862f-adcb2a2a8df1
Spec:
  Channel Template:
    API Version:  messaging.knative.dev/v1alpha1
    Kind:         KafkaChannel
    Spec:
Status:
  Address:
    URL:  http://testing-dbevents-kn-channel.cluster-1.svc.cluster.local
  Channel:
    API Version:  messaging.knative.dev/v1alpha1
    Kind:         KafkaChannel
    Name:         testing-dbevents
    Namespace:    cluster-1
  Conditions:
    Last Transition Time:  2020-07-07T02:00:56Z
    Status:                True
    Type:                  Ready
  Observed Generation:     1
Events:
  Type    Reason             Age              From                Message
  ----    ------             ----             ----                -------
  Normal  ChannelReconciled  1m (x4 over 1m)  channel-controller  Channel reconciled: "cluster-1/testing-dbevents"

```

We can also interrogate the cluster, to list our *kafka channels*: 

```py
 oc get kafkachannels -n cluster-1
NAME                  READY     REASON    URL                                                                 AGE
default-kne-trigger   True                http://default-kne-trigger-kn-channel.cluster-1.svc.cluster.local   53m
testing-dbevents      True                http://testing-dbevents-kn-channel.cluster-1.svc.cluster.local      5m54s

```

At this point, we'll go ahead and apply the rest of our channel resources to further prepare our Openshift cluster for the demo: 

```py
oc apply -f ./src/main/k8s/cloud-1/eventing/testing-dbeventaggregate-channels.yaml -n cluster-1
channel.messaging.knative.dev/testing-dbeventaggregate created
oc apply -f ./src/main/k8s/cloud-1/eventing/testing-dbevents-channel.yaml -n cluster-2
channel.messaging.knative.dev/testing-dbevents created
 oc apply -f ./src/main/k8s/cloud-1/eventing/testing-dbeventaggregate-channels.yaml -n cluster-2
channel.messaging.knative.dev/testing-dbeventaggregate created
```

We should see our full complement of channels if we interrogate one of our namespaces: 

```py
oc get channels -n cluster-1
NAME                       READY     REASON    URL                                                                      AGE
testing-dbeventaggregate   True                http://testing-dbeventaggregate-kn-channel.cluster-1.svc.cluster.local   17m
testing-dbevents           True                http://testing-dbevents-kn-channel.cluster-1.svc.cluster.local           25m
```

#### Installing the Event Sink 
As we have previously installed the Red Hat - Camel K Operator, we can leverage Camel-K to run *Apache Camel* based integrations as AMQP based event receivers along our event mesh, and ultimately, act as a sink for our *Knative Eventing* based **event bus**. 

##### Using the Kamel CLI

While this document will not describe the installation of the Kamel CLI, it is well described here: https://camel.apache.org/camel-k/latest/installation/openshift.html

*Please note*: this demo will not attempt to demonstrate proper CICD procedures for use of Camel-K; however, please stay tuned to this series for *Cloud Native Integration: Continuous Integration and Deployment*. It is also worth noting the following Apache Camel example: https://camel.apache.org/camel-k/latest/tutorials/tekton/tekton.html 

In the meantime, let's use the Kamel CLI to install our event sink for one of the channels we have installed into our namespace *cluster-1* - *testing-dbevents*: 

```py
kamel run -n cluster-1 --trait deployer.kind=deployment --trait container.limit-cpu=500m --trait container.limit-memory=512Mi --trait container.name=cloudnative-integration ./src/main/java/io/entropic/integration/examples/eventmesh/AMQPSinkIntegration.java
```

Upon issuing this CLI command, the Camel-K Operator will do a few things: 
* It will create a build kit 
* It will create a builder pod 
* It will pull resources, from the Camel-K catalogue that match a specific version of *Apache Camel*. This means our developers will not need to labouriously configure underlying camel component resources!
* It will create an *Integration* deployment based on a set of traits that we use (in our case some requests and limits as well as a deployment type)
* It will care and feed for our integration, and provide us a path towards integration with other systems such as our knative eventing *event bus*, API Management via 3Scale, as well as other Ingress controller and Service Mesh features through the use of Istio 

Right off the bat, we see a few things in our namespace *cluster-1*: 

```py
 oc get pods -w -n cluster-1
NAME                                       READY     STATUS      RESTARTS   AGE
amqp-sink-integration-64dc6fcd4d-szl4t     1/1       Running     0          20m
camel-k-kit-bs1un3ubhuc7jr4mqb30-1-build   0/1       Completed   0          21m
camel-k-kit-bs1un3ubhuc7jr4mqb30-builder   0/1       Completed   0          22m
```
We notice a build kit pod as well as the actual integration deployment, when we describe our Camel-K integration it gives us more information in regards to what these things are and how they build out our integration: 

```py
oc describe integration amqp-sink-integration 
Name:         amqp-sink-integration
Namespace:    cluster-1
Labels:       <none>
Annotations:  <none>
API Version:  camel.apache.org/v1
Kind:         Integration
Metadata:
  Creation Timestamp:  2020-07-07T03:27:11Z
  Generation:          1
  Resource Version:    1679979
  Self Link:           /apis/camel.apache.org/v1/namespaces/cluster-1/integrations/amqp-sink-integration
  UID:                 ef4a8c8f-7cd7-4ff7-a025-00532c7087ff
Spec:
  Sources:
    Content:  package io.entropic.integration.examples.eventmesh;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;

public class AMQPSinkIntegration extends RouteBuilder {

  @BindToRegistry
  public javax.jms.ConnectionFactory connectionFactory() {
    return new org.apache.qpid.jms.JmsConnectionFactory(
        "amqp://cloud1-router-mesh.cluster-1:5672?transport.trustAll=true&transport.verifyHost=false");
  }

  @Override
  public void configure() throws Exception {
    
    from("amqp:queue:test.db-events")
      .log("**Received message ${body}**")
      .setBody().simple("${body} processed by AMQPSinkIntegration")
      .to("knative:channel/testing-dbevents")
      .log("**sent message to knative channel ${body} **");

  }

}

    Name:  AMQPSinkIntegration.java
  Traits:
    Container:
      Configuration:
        Limit - Cpu:     500m
        Limit - Memory:  512Mi
        Name:            cloudnative-integration
    Deployer:
      Configuration:
        Kind:  deployment
Status:
  Capabilities:
    platform-http
  Conditions:
    Last Transition Time:  2020-07-07T03:27:11Z
    Last Update Time:      2020-07-07T03:27:11Z
    Message:               camel-k
    Reason:                IntegrationPlatformAvailable
    Status:                True
    Type:                  IntegrationPlatformAvailable
    Last Transition Time:  2020-07-07T03:29:33Z
    Last Update Time:      2020-07-07T03:29:33Z
    Message:               kit-bs1un3ubhuc7jr4mqb30
    Reason:                IntegrationKitAvailable
    Status:                True
    Type:                  IntegrationKitAvailable
    Last Transition Time:  2020-07-07T03:29:37Z
    Last Update Time:      2020-07-07T03:29:37Z
    Message:               different controller strategy used (deployment)
    Reason:                CronJobNotAvailableReason
    Status:                False
    Type:                  CronJobAvailable
    Last Transition Time:  2020-07-07T03:29:37Z
    Last Update Time:      2020-07-07T03:29:37Z
    Message:               deployment name is amqp-sink-integration
    Reason:                DeploymentAvailable
    Status:                True
    Type:                  DeploymentAvailable
    Last Transition Time:  2020-07-07T03:29:37Z
    Last Update Time:      2020-07-07T03:29:37Z
    Message:               different controller strategy used (deployment)
    Reason:                KnativeServiceNotAvailable
    Status:                False
    Type:                  KnativeServiceAvailable
  Dependencies:
    camel:amqp
    mvn:org.apache.camel.k/camel-k-loader-java
    mvn:org.apache.camel.k/camel-k-runtime-main
    mvn:org.apache.camel.k:camel-knative
  Digest:            v3y2DwE-7npkTmwW-3zeh8sBAn2vpJHSXHsFabGHgk3I
  Image:             image-registry.openshift-image-registry.svc:5000/cluster-1/camel-k-kit-bs1un3ubhuc7jr4mqb30@sha256:cde967f32c00d46020f7a5bd726d67145162e7abaf20a4abbf9bbd53dd191881
  Kit:               kit-bs1un3ubhuc7jr4mqb30
  Phase:             Running
  Platform:          camel-k
  Profile:           Knative
  Replicas:          1
  Runtime Provider:  main
  Runtime Version:   1.3.0.fuse-jdk11-800012-redhat-00001
  Version:           1.0
Events:
  Type    Reason                       Age   From                                Message
  ----    ------                       ----  ----                                -------
  Normal  ReasonRelatedObjectChanged   24m   camel-k-integration-kit-controller  Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Integration Kit) changed phase to Build Running
  Normal  IntegrationPhaseUpdated      24m   camel-k-integration-controller      Integration amqp-sink-integration in phase Waiting For Platform
  Normal  IntegrationConditionChanged  24m   camel-k-integration-controller      IntegrationPlatformAvailable for Integration amqp-sink-integration: camel-k
  Normal  IntegrationPhaseUpdated      24m   camel-k-integration-controller      Integration amqp-sink-integration in phase Initialization
  Normal  IntegrationConditionChanged  24m   camel-k-integration-controller      No IntegrationKitAvailable for Integration amqp-sink-integration: creating a new integration kit
  Normal  IntegrationPhaseUpdated      24m   camel-k-integration-controller      Integration amqp-sink-integration in phase Building Kit
  Normal  ReasonRelatedObjectChanged   24m   camel-k-integration-kit-controller  Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Integration Kit) changed phase to Build Submitted
  Normal  ReasonRelatedObjectChanged   24m   camel-k-build-controller            Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Build) changed phase to Scheduling
  Normal  ReasonRelatedObjectChanged   24m   camel-k-build-controller            Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Build) changed phase to Pending
  Normal  ReasonRelatedObjectChanged   24m   camel-k-build-controller            Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Build) changed phase to Running
  Normal  IntegrationConditionChanged  24m   camel-k-integration-controller      No IntegrationPlatformAvailable for Integration amqp-sink-integration: camel-k
  Normal  ReasonRelatedObjectChanged   22m   camel-k-build-controller            Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Build) changed phase to Succeeded
  Normal  ReasonRelatedObjectChanged   22m   camel-k-integration-kit-controller  Integration amqp-sink-integration dependent resource kit-bs1un3ubhuc7jr4mqb30 (Integration Kit) changed phase to Ready
  Normal  IntegrationConditionChanged  22m   camel-k-integration-controller      IntegrationKitAvailable for Integration amqp-sink-integration: kit-bs1un3ubhuc7jr4mqb30
  Normal  IntegrationPhaseUpdated      22m   camel-k-integration-controller      Integration amqp-sink-integration in phase Deploying
  Normal  IntegrationConditionChanged  22m   camel-k-integration-controller      No CronJobAvailable for Integration amqp-sink-integration: different controller strategy used (deployment)
  Normal  IntegrationConditionChanged  22m   camel-k-integration-controller      DeploymentAvailable for Integration amqp-sink-integration: deployment name is amqp-sink-integration
  Normal  IntegrationConditionChanged  22m   camel-k-integration-controller      No KnativeServiceAvailable for Integration amqp-sink-integration: different controller strategy used (deployment)
  Normal  IntegrationPhaseUpdated      22m   camel-k-integration-controller      Integration amqp-sink-integration in phase Running

```

We'll notice our integration code (which can be in nearly any java variant), as well as a number of things about our inevitable run time environment that our build kit and builder pod ultimately have deployed. 

Taking a look at our builder pod, we notice some very familiar things: 
* Maven dependencies being fetched 
* Wiring up of an underlying JVM environment 
* Building of an image 

A quick snippet from our buil kit pod shows some very familiar things: 

```py 
{"level":"info","ts":1594092510.535882,"logger":"camel-k.builder","msg":"executing step","step":"github.com/apache/camel-k/pkg/builder/IncrementalImageContext","phase":30,"name":"kit-bs1un3ubhuc7jr4mqb30","task":"builder"}
{"level":"info","ts":1594092510.59943,"logger":"camel-k.builder","msg":"step done in 0.063516 seconds","step":"github.com/apache/camel-k/pkg/builder/IncrementalImageContext","phase":30,"name":"kit-bs1un3ubhuc7jr4mqb30","task":"builder"}
{"level":"info","ts":1594092510.5994673,"logger":"camel-k.builder","msg":"executing step","step":"github.com/apache/camel-k/pkg/builder/s2i/Publisher","phase":40,"name":"kit-bs1un3ubhuc7jr4mqb30","task":"builder"}
{"level":"info","ts":1594092572.896473,"logger":"camel-k.builder","msg":"step done in 62.296992 seconds","step":"github.com/apache/camel-k/pkg/builder/s2i/Publisher","phase":40,"name":"kit-bs1un3ubhuc7jr4mqb30","task":"builder"}
{"level":"info","ts":1594092572.8965232,"logger":"camel-k.builder","msg":"dependencies: [camel:amqp mvn:org.apache.camel.k/camel-k-loader-java mvn:org.apache.camel.k/camel-k-runtime-main mvn:org.apache.camel.k:camel-knative]"}
{"level":"info","ts":1594092572.896557,"logger":"camel-k.builder","msg":"artifacts: [org.apache.camel:camel-amqp:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-jms:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-support:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-spring:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-xml-jaxb:jar:3.1.0.fuse-jdk11-800011-redhat-00001 jakarta.xml.bind:jakarta.xml.bind-api:jar:2.3.2 jakarta.activation:jakarta.activation-api:jar:1.2.1 com.sun.xml.bind:jaxb-core:jar:2.3.0 com.sun.xml.bind:jaxb-impl:jar:2.3.0 org.apache.camel:camel-xml-jaxp:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-core-xml:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.springframework:spring-core:jar:5.2.3.RELEASE org.springframework:spring-jcl:jar:5.2.3.RELEASE org.springframework:spring-aop:jar:5.2.3.RELEASE org.springframework:spring-expression:jar:5.2.3.RELEASE org.springframework:spring-jms:jar:5.2.3.RELEASE org.springframework:spring-messaging:jar:5.2.3.RELEASE org.springframework:spring-context:jar:5.2.3.RELEASE org.springframework:spring-tx:jar:5.2.3.RELEASE org.springframework:spring-beans:jar:5.2.3.RELEASE org.apache.geronimo.specs:geronimo-jms_2.0_spec:jar:1.0.0.alpha-2-redhat-2 org.apache.qpid:qpid-jms-client:jar:0.48.0.redhat-00004 io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.45.Final-redhat-00001 io.netty:netty-transport-native-unix-common:jar:4.1.45.Final-redhat-00001 io.netty:netty-transport-native-kqueue:jar:4.1.45.Final-redhat-00001 org.apache.qpid:proton-j:jar:0.33.3.redhat-00001 io.netty:netty-buffer:jar:4.1.45.Final-redhat-00002 io.netty:netty-common:jar:4.1.45.Final-redhat-00002 io.netty:netty-handler:jar:4.1.45.Final-redhat-00002 io.netty:netty-codec:jar:4.1.45.Final-redhat-00002 io.netty:netty-transport:jar:4.1.45.Final-redhat-00002 io.netty:netty-resolver:jar:4.1.45.Final-redhat-00002 io.netty:netty-codec-http:jar:4.1.45.Final-redhat-00002 org.apache.camel.k:camel-k-loader-java:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-k-runtime-core:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-core-engine:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-api:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-base:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-management-api:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-util:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-endpointdsl:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.jooq:joor:jar:0.9.12 org.apache.camel.k:camel-k-runtime-main:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-main:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-caffeine-lrucache:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.github.ben-manes.caffeine:caffeine:jar:2.8.1 org.apache.camel:camel-headersmap:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.cedarsoftware:java-util:jar:1.40.0 org.apache.camel:camel-bean:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.logging.log4j:log4j-core:jar:2.13.1.redhat-00001 org.apache.logging.log4j:log4j-api:jar:2.13.1.redhat-00001 org.apache.logging.log4j:log4j-slf4j-impl:jar:2.13.1.redhat-00001 org.apache.camel.k:camel-knative:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.slf4j:slf4j-api:jar:1.7.30 org.apache.camel:camel-cloud:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.fasterxml.jackson.core:jackson-databind:jar:2.10.3.redhat-00001 com.fasterxml.jackson.core:jackson-annotations:jar:2.10.3.redhat-00001 com.fasterxml.jackson.core:jackson-core:jar:2.10.3.redhat-00001 com.fasterxml.jackson.datatype:jackson-datatype-jdk8:jar:2.10.3.redhat-00001 org.apache.camel.k:camel-knative-api:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-knative-http:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-k-runtime-http:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-platform-http:jar:3.1.0.fuse-jdk11-800011-redhat-00001 io.vertx:vertx-web:jar:3.8.5.redhat-00005 io.vertx:vertx-auth-common:jar:3.8.5.redhat-00005 io.vertx:vertx-bridge-common:jar:3.8.5.redhat-00005 io.vertx:vertx-web-client:jar:3.8.5.redhat-00005 io.vertx:vertx-web-common:jar:3.8.5.redhat-00005 io.vertx:vertx-core:jar:3.8.5.redhat-00005 io.netty:netty-handler-proxy:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-socks:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-http2:jar:4.1.45.Final-redhat-00001 io.netty:netty-resolver-dns:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-dns:jar:4.1.45.Final-redhat-00001]"}
{"level":"info","ts":1594092572.8966222,"logger":"camel-k.builder","msg":"artifacts selected: [org.apache.camel:camel-amqp:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-jms:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-support:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-spring:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-xml-jaxb:jar:3.1.0.fuse-jdk11-800011-redhat-00001 jakarta.xml.bind:jakarta.xml.bind-api:jar:2.3.2 jakarta.activation:jakarta.activation-api:jar:1.2.1 com.sun.xml.bind:jaxb-core:jar:2.3.0 com.sun.xml.bind:jaxb-impl:jar:2.3.0 org.apache.camel:camel-xml-jaxp:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-core-xml:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.springframework:spring-core:jar:5.2.3.RELEASE org.springframework:spring-jcl:jar:5.2.3.RELEASE org.springframework:spring-aop:jar:5.2.3.RELEASE org.springframework:spring-expression:jar:5.2.3.RELEASE org.springframework:spring-jms:jar:5.2.3.RELEASE org.springframework:spring-messaging:jar:5.2.3.RELEASE org.springframework:spring-context:jar:5.2.3.RELEASE org.springframework:spring-tx:jar:5.2.3.RELEASE org.springframework:spring-beans:jar:5.2.3.RELEASE org.apache.geronimo.specs:geronimo-jms_2.0_spec:jar:1.0.0.alpha-2-redhat-2 org.apache.qpid:qpid-jms-client:jar:0.48.0.redhat-00004 io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.45.Final-redhat-00001 io.netty:netty-transport-native-unix-common:jar:4.1.45.Final-redhat-00001 io.netty:netty-transport-native-kqueue:jar:4.1.45.Final-redhat-00001 org.apache.qpid:proton-j:jar:0.33.3.redhat-00001 io.netty:netty-buffer:jar:4.1.45.Final-redhat-00002 io.netty:netty-common:jar:4.1.45.Final-redhat-00002 io.netty:netty-handler:jar:4.1.45.Final-redhat-00002 io.netty:netty-codec:jar:4.1.45.Final-redhat-00002 io.netty:netty-transport:jar:4.1.45.Final-redhat-00002 io.netty:netty-resolver:jar:4.1.45.Final-redhat-00002 io.netty:netty-codec-http:jar:4.1.45.Final-redhat-00002 org.apache.camel.k:camel-k-loader-java:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-k-runtime-core:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-core-engine:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-api:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-base:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-management-api:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-util:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-endpointdsl:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.jooq:joor:jar:0.9.12 org.apache.camel.k:camel-k-runtime-main:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-main:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.camel:camel-caffeine-lrucache:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.github.ben-manes.caffeine:caffeine:jar:2.8.1 org.apache.camel:camel-headersmap:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.cedarsoftware:java-util:jar:1.40.0 org.apache.camel:camel-bean:jar:3.1.0.fuse-jdk11-800011-redhat-00001 org.apache.logging.log4j:log4j-core:jar:2.13.1.redhat-00001 org.apache.logging.log4j:log4j-api:jar:2.13.1.redhat-00001 org.apache.logging.log4j:log4j-slf4j-impl:jar:2.13.1.redhat-00001 org.apache.camel.k:camel-knative:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.slf4j:slf4j-api:jar:1.7.30 org.apache.camel:camel-cloud:jar:3.1.0.fuse-jdk11-800011-redhat-00001 com.fasterxml.jackson.core:jackson-databind:jar:2.10.3.redhat-00001 com.fasterxml.jackson.core:jackson-annotations:jar:2.10.3.redhat-00001 com.fasterxml.jackson.core:jackson-core:jar:2.10.3.redhat-00001 com.fasterxml.jackson.datatype:jackson-datatype-jdk8:jar:2.10.3.redhat-00001 org.apache.camel.k:camel-knative-api:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-knative-http:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel.k:camel-k-runtime-http:jar:1.3.0.fuse-jdk11-800012-redhat-00001 org.apache.camel:camel-platform-http:jar:3.1.0.fuse-jdk11-800011-redhat-00001 io.vertx:vertx-web:jar:3.8.5.redhat-00005 io.vertx:vertx-auth-common:jar:3.8.5.redhat-00005 io.vertx:vertx-bridge-common:jar:3.8.5.redhat-00005 io.vertx:vertx-web-client:jar:3.8.5.redhat-00005 io.vertx:vertx-web-common:jar:3.8.5.redhat-00005 io.vertx:vertx-core:jar:3.8.5.redhat-00005 io.netty:netty-handler-proxy:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-socks:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-http2:jar:4.1.45.Final-redhat-00001 io.netty:netty-resolver-dns:jar:4.1.45.Final-redhat-00001 io.netty:netty-codec-dns:jar:4.1.45.Final-redhat-00001]"}
{"level":"info","ts":1594092572.8966591,"logger":"camel-k.builder","msg":"base image: registry.access.redhat.com/ubi8/openjdk-11:1.3"}
{"level":"info","ts":1594092572.8966627,"logger":"camel-k.builder","msg":"resolved base image: registry.access.redhat.com/ubi8/openjdk-11:1.3"}
{"level":"info","ts":1594092572.8966658,"logger":"camel-k.builder","msg":"resolved image: image-registry.openshift-image-registry.svc:5000/cluster-1/camel-k-kit-bs1un3ubhuc7jr4mqb30:1678529"}

```

And ultimately, our build pod shows the creation of the ultimate image that represents our Integration: 

```py 
oc logs camel-k-kit-bs1un3ubhuc7jr4mqb30-1-build -f 
Caching blobs under "/var/cache/blobs".

Pulling image registry.access.redhat.com/ubi8/openjdk-11:1.3 ...
Getting image source signatures
Copying blob sha256:1b99828eddf5297ca28fa7eac7f47a40d36a693c628311406788a14dfe75e076
Copying blob sha256:fba81d8872a85de345b80cc69b1f23309ee284950d45dbee2bd8bf9bf17c60a7
Copying blob sha256:e96e3a1df3b2b1e01f9614725b50ea4d1d8e480980e456815ade3c7afca978d7
Copying config sha256:d43c43b348f8c2cfcbe4beff8c36dd8c73cd78cb89e3912190b0b357904f367d
Writing manifest to image destination
Storing signatures
STEP 1: FROM registry.access.redhat.com/ubi8/openjdk-11:1.3
STEP 2: ADD . /deployments
time="2020-07-07T03:29:09Z" level=info msg="Image operating system mismatch: image uses \"\", expecting \"linux\""
time="2020-07-07T03:29:09Z" level=info msg="Image architecture mismatch: image uses \"\", expecting \"amd64\""
--> 1a55bc7e5af
STEP 3: USER 1000
time="2020-07-07T03:29:10Z" level=info msg="Image operating system mismatch: image uses \"\", expecting \"linux\""
time="2020-07-07T03:29:10Z" level=info msg="Image architecture mismatch: image uses \"\", expecting \"amd64\""
--> 562ea4ed86e
STEP 4: ENV "OPENSHIFT_BUILD_NAME"="camel-k-kit-bs1un3ubhuc7jr4mqb30-1" "OPENSHIFT_BUILD_NAMESPACE"="cluster-1"
time="2020-07-07T03:29:10Z" level=info msg="Image operating system mismatch: image uses \"\", expecting \"linux\""
time="2020-07-07T03:29:10Z" level=info msg="Image architecture mismatch: image uses \"\", expecting \"amd64\""
--> 2a358bb17bf
STEP 5: LABEL "io.openshift.build.name"="camel-k-kit-bs1un3ubhuc7jr4mqb30-1" "io.openshift.build.namespace"="cluster-1"
STEP 6: COMMIT temp.builder.openshift.io/cluster-1/camel-k-kit-bs1un3ubhuc7jr4mqb30-1:bd99c4f2
time="2020-07-07T03:29:10Z" level=info msg="Image operating system mismatch: image uses \"\", expecting \"linux\""
time="2020-07-07T03:29:10Z" level=info msg="Image architecture mismatch: image uses \"\", expecting \"amd64\""
--> 59f4e2ff6c4
59f4e2ff6c47ff3cf2e065bb8b6da3a97e87c54f4f48cf30b2213e787c3bc636

Pushing image image-registry.openshift-image-registry.svc:5000/cluster-1/camel-k-kit-bs1un3ubhuc7jr4mqb30:1678529 ...
Getting image source signatures
Copying blob sha256:e96e3a1df3b2b1e01f9614725b50ea4d1d8e480980e456815ade3c7afca978d7
Copying blob sha256:fba81d8872a85de345b80cc69b1f23309ee284950d45dbee2bd8bf9bf17c60a7
Copying blob sha256:50fdb1a6f2f893727eed92d3fb141a71b9abde2b839c1d33d08265a3cd313dc7
Copying blob sha256:1b99828eddf5297ca28fa7eac7f47a40d36a693c628311406788a14dfe75e076
Copying config sha256:59f4e2ff6c47ff3cf2e065bb8b6da3a97e87c54f4f48cf30b2213e787c3bc636
Writing manifest to image destination
Storing signatures
Successfully pushed image-registry.openshift-image-registry.svc:5000/cluster-1/camel-k-kit-bs1un3ubhuc7jr4mqb30@sha256:cde967f32c00d46020f7a5bd726d67145162e7abaf20a4abbf9bbd53dd191881
Push successful

```

We now have our image created by our Integration Build Kit in the local Openshift Image registry. 

All with a simple invocation of the *Kamel CLI*. 

At this point, we'll also deploy our *event sink* to *cluster-2*, as both *cluster-1* and *cluster-2* will have event receivers that are participating in our event mesh: 

```py
kamel run -n cluster-2 --trait deployer.kind=deployment --trait container.limit-cpu=500m --trait container.limit-memory=512Mi --trait container.name=cloudnative-integration ./src/main/java/io/entropic/integration/examples/eventmesh/AMQPSinkIntegration.java
```

We'll notice that we have a similar set of pods in *cluster-2*: 

```py
oc get pods -w -n cluster-2
NAME                                       READY     STATUS      RESTARTS   AGE
amqp-sink-integration-76bb7c667f-5mdm2     1/1       Running     0          11m
camel-k-kit-bs1v76ubhuc7jr4mqb3g-1-build   0/1       Completed   0          12m
camel-k-kit-bs1v76ubhuc7jr4mqb3g-builder   0/1       Completed   0          13m
cloud2-router-mesh-d566476ff-bqtrx         1/1       Running     0          3h
default-broker-filter-b5967fd6c-fs87l      1/1       Running     0          2h
default-broker-ingress-5485ff998c-k468n    1/1       Running     0          2h
interconnect-operator-745d45688b-7snf2     1/1       Running     0          3h
```

Taking a peak at the logs of our integration pod, we'll notice it has linked up with our event mesh and some familar *Apache Camel* logging: 

```py 
oc logs amqp-sink-integration-768c959d79-tz4zc -f 
2020-07-07 04:19:59.179 INFO  [main] LRUCacheFactory - Detected and using LURCacheFactory: camel-caffeine-lrucache
2020-07-07 04:19:59.664 INFO  [main] ApplicationRuntime - Add listener: org.apache.camel.k.listener.RuntimeConfigurer@6bf08014
2020-07-07 04:19:59.664 INFO  [main] ApplicationRuntime - Add listener: org.apache.camel.k.listener.ContextConfigurer@6ee4d9ab
2020-07-07 04:19:59.665 INFO  [main] ApplicationRuntime - Add listener: org.apache.camel.k.listener.RoutesConfigurer@302f7971
2020-07-07 04:19:59.666 INFO  [main] ApplicationRuntime - Add listener: org.apache.camel.k.listener.RoutesDumper@9573584
2020-07-07 04:19:59.666 INFO  [main] ApplicationRuntime - Add listener: org.apache.camel.k.listener.PropertiesFunctionsConfigurer@352c1b98
2020-07-07 04:19:59.670 INFO  [main] ApplicationRuntime - Listener org.apache.camel.k.listener.RuntimeConfigurer@6bf08014 executed in phase Starting
2020-07-07 04:19:59.679 INFO  [main] ApplicationRuntime - Listener org.apache.camel.k.listener.PropertiesFunctionsConfigurer@352c1b98 executed in phase Starting
2020-07-07 04:19:59.680 INFO  [main] BaseMainSupport - Using properties from: 
2020-07-07 04:19:59.763 INFO  [main] RuntimeSupport - Looking up loader for language: java
2020-07-07 04:19:59.765 INFO  [main] RuntimeSupport - Found loader org.apache.camel.k.loader.java.JavaSourceLoader@36060e for language java from service definition
2020-07-07 04:20:02.571 INFO  [main] RoutesConfigurer - Loading routes from: file:/etc/camel/sources/i-source-000/AMQPSinkIntegration.java?language=java
2020-07-07 04:20:02.571 INFO  [main] ApplicationRuntime - Listener org.apache.camel.k.listener.RoutesConfigurer@302f7971 executed in phase ConfigureRoutes
2020-07-07 04:20:02.875 INFO  [main] RuntimeSupport - Found customizer org.apache.camel.k.http.PlatformHttpServiceContextCustomizer@1b70203f with id platform-http from service definition
2020-07-07 04:20:02.876 INFO  [main] RuntimeSupport - Apply ContextCustomizer with id=platform-http and type=org.apache.camel.k.http.PlatformHttpServiceContextCustomizer
2020-07-07 04:20:03.079 INFO  [main] PlatformHttpServiceEndpoint - Creating new Vert.x instance
2020-07-07 04:20:04.168 INFO  [vert.x-eventloop-thread-1] PlatformHttpServer - Vert.x HttpServer started on 0.0.0.0:8080
2020-07-07 04:20:04.175 INFO  [main] ApplicationRuntime - Listener org.apache.camel.k.listener.ContextConfigurer@6ee4d9ab executed in phase ConfigureContext
2020-07-07 04:20:04.176 INFO  [main] AbstractCamelContext - Apache Camel 3.1.0.fuse-jdk11-800011-redhat-00001 (CamelContext: camel-k) is starting
2020-07-07 04:20:04.177 INFO  [main] DefaultManagementStrategy - JMX is disabled
2020-07-07 04:20:04.179 INFO  [main] HeadersMapFactoryResolver - Detected and using HeadersMapFactory: camel-headersmap
2020-07-07 04:20:04.566 INFO  [main] BaseMainSupport - Autowired property: connectionFactory on component: AMQPComponent as exactly one instance of type: javax.jms.ConnectionFactory found in the registry
2020-07-07 04:20:04.766 INFO  [main] KnativeComponent - found knative transport: org.apache.camel.component.knative.http.KnativeHttpTransport@68fe48d7 for protocol: http
2020-07-07 04:20:05.563 INFO  [main] AbstractCamelContext - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2020-07-07 04:20:11.665 INFO  [AmqpProvider :(1):[amqp://cloud2-router-mesh.cluster-2:5672]] SaslMechanismFinder - Best match for SASL auth was: SASL-ANONYMOUS
2020-07-07 04:20:11.767 INFO  [AmqpProvider :(1):[amqp://cloud2-router-mesh.cluster-2:5672]] JmsConnection - Connection ID:61991487-4cbc-4633-9bc4-36053a86d8f9:1 connected to remote Broker: amqp://cloud2-router-mesh.cluster-2:5672
2020-07-07 04:20:11.767 INFO  [main] AbstractCamelContext - Route: route1 started and consuming from: amqp://queue:test.db-events
2020-07-07 04:20:11.770 INFO  [main] AbstractCamelContext - Total 1 routes, of which 1 are started
2020-07-07 04:20:11.770 INFO  [main] AbstractCamelContext - Apache Camel 3.1.0.fuse-jdk11-800011-redhat-00001 (CamelContext: camel-k) started in 7.595 seconds
2020-07-07 04:20:11.771 INFO  [main] ApplicationRuntime - Listener org.apache.camel.k.listener.RoutesDumper@9573584 executed in phase Started

```

#### Deploying Serverless Integrations on the *Event Bus* 
Now that we have an *event sink* to persist events onto our event bus, let's leverage the power of Camel-K to have integrations participate on the event bus: 

```py


```

Upon executing this CLI command, we'll notice something a little different than our previous Camel-K Integration deployment: 

```py 
oc get pods -w -n cluster-1
NAME                                                              READY     STATUS        RESTARTS   AGE
amqp-sink-integration-64dc6fcd4d-szl4t                            1/1       Running       0          1h
camel-k-kit-bs1un3ubhuc7jr4mqb30-1-build                          0/1       Completed     0          1h
camel-k-kit-bs1un3ubhuc7jr4mqb30-builder                          0/1       Completed     0          1h
camel-k-kit-bs1vjgmbhuc7jr4mqb4g-1-build                          0/1       Completed     0          2m
camel-k-kit-bs1vjgmbhuc7jr4mqb4g-builder                          0/1       Completed     0          2m
cloud1-router-mesh-7f698d8c65-xft27                               1/1       Running       0          4h
default-broker-filter-b5967fd6c-rs8vm                             1/1       Running       0          2h
default-broker-ingress-6d4c6474c8-9gd4m                           1/1       Running       0          2h
event-bus-transformation-integration-wqksm-deployment-5bf8jkb7x   2/2       Terminating   0          1m
interconnect-operator-5684994fbf-587dk                            1/1       Running       0          4h
event-bus-transformation-integration-wqksm-deployment-5bf8jkb7x   2/2       Terminating   0         1m
event-bus-transformation-integration-wqksm-deployment-5bf8jkb7x   0/2       Terminating   0         1m
event-bus-transformation-integration-wqksm-deployment-5bf8jkb7x   0/2       Terminating   0         1m
event-bus-transformation-integration-wqksm-deployment-5bf8jkb7x   0/2       Terminating   0         1m
```

We'll notice that our integration runs and then scales back down to zero. This is because we are using *Knative Serving* and our Integration is now serverless and scaled to 0. When we interrogate K8s, we'll notice our integration is ready; however, we have 0 replicas running: 

```py
oc get integrations -n cluster-1
NAME                                   PHASE     KIT                        REPLICAS
amqp-sink-integration                  Running   kit-bs1un3ubhuc7jr4mqb30   1
event-bus-transformation-integration   Running   kit-bs1vjgmbhuc7jr4mqb4g   0
```

And if we interrogate our *knative serving* system, we'll notice we have a viable knative serving revision for our integration: 

```py
oc get revisions.serving.knative.dev -n cluster-1
NAME                                         CONFIG NAME                            K8S SERVICE NAME                             GENERATION   READY     REASON
event-bus-transformation-integration-wqksm   event-bus-transformation-integration   event-bus-transformation-integration-wqksm   1            True      
```

We will also notice that we have a subscription to a knative event channel:

```py
oc get subscriptions.messaging.knative.dev 
NAME                                                    READY     REASON    AGE
testing-dbevents-event-bus-transformation-integration   True                9m38s
```

Describing the subscription resource: 

```py
[mcostell@work cloud-native-event-mesh]$ oc describe subscriptions.messaging.knative.dev testing-dbevents-event-bus-transformation-integration 
Name:         testing-dbevents-event-bus-transformation-integration
Namespace:    cluster-1
Labels:       camel.apache.org/generation=1
              camel.apache.org/integration=event-bus-transformation-integration
Annotations:  messaging.knative.dev/creator=system:serviceaccount:openshift-operators:camel-k-operator
              messaging.knative.dev/lastModifier=system:serviceaccount:openshift-operators:camel-k-operator
API Version:  messaging.knative.dev/v1beta1
Kind:         Subscription
Metadata:
  Creation Timestamp:  2020-07-07T04:29:19Z
  Finalizers:
    subscriptions.messaging.knative.dev
  Generation:  1
  Owner References:
    API Version:           camel.apache.org/v1
    Block Owner Deletion:  true
    Controller:            true
    Kind:                  Integration
    Name:                  event-bus-transformation-integration
    UID:                   f21518f9-8906-4cb8-a164-fecf2df7fb6c
  Resource Version:        1714673
  Self Link:               /apis/messaging.knative.dev/v1beta1/namespaces/cluster-1/subscriptions/testing-dbevents-event-bus-transformation-integration
  UID:                     968a8794-9744-414f-91b4-d6226670fa4d
Spec:
  Channel:
    API Version:  messaging.knative.dev/v1alpha1
    Kind:         Channel
    Name:         testing-dbevents
  Subscriber:
    Ref:
      API Version:  serving.knative.dev/v1
      Kind:         Service
      Name:         event-bus-transformation-integration
      Namespace:    cluster-1
Status:
  Conditions:
    Last Transition Time:  2020-07-07T04:29:29Z
    Status:                True
    Type:                  AddedToChannel
    Last Transition Time:  2020-07-07T04:29:30Z
    Status:                True
    Type:                  ChannelReady
    Last Transition Time:  2020-07-07T04:29:30Z
    Status:                True
    Type:                  Ready
    Last Transition Time:  2020-07-07T04:29:30Z
    Status:                True
    Type:                  Resolved
  Observed Generation:     1
  Physical Subscription:
    Subscriber URI:  http://event-bus-transformation-integration.cluster-1.svc.cluster.local
Events:
  Type     Reason                               Age                From                     Message
  ----     ------                               ----               ----                     -------
  Normal   FinalizerUpdate                      10m                subscription-controller  Updated "testing-dbevents-event-bus-transformation-integration" finalizers
  Warning  SubscriberResolveFailed              10m (x5 over 10m)  subscription-controller  Failed to resolve spec.subscriber: address not set for &ObjectReference{Kind:Service,Namespace:cluster-1,Name:event-bus-transformation-integration,UID:,APIVersion:serving.knative.dev/v1,ResourceVersion:,FieldPath:,}
  Normal   SubscriberSync                       10m                subscription-controller  Subscription was synchronized to channel "testing-dbevents"
  Warning  SubscriptionNotMarkedReadyByChannel  10m                subscription-controller  channel.Status.SubscribableStatus is nil
  Normal   SubscriptionReconciled               10m (x3 over 10m)  subscription-controller  Subscription reconciled: "cluster-1/testing-dbevents-event-bus-transformation-integration"
```

We now have an integration that is connected to the *event bus* and an *event sink* that acts as a source for our event bus and ultimately allows our serverless integrations to participate with our event mesh. 

Apply this same integration to *cluster-2* and then we will be ready to demonstrate some of the incredible power or our *event mesh* and *event bus* as we demonstrate active-passive, active-active and other event mesh topologies along with serverless integration in a way that makes us truly ***Cloud Native***!!!
