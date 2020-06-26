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

### Installing the Demo 

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

Initially, let's create a key pair secret out of our CA private key and root cert, and let's place them in a namespace called "cloud-event-mesh-demo": 

```
oc create namespace cloud-event-mesh-demo 

oc create secret generic cloud-native-event-mesh-demo-ca-pair --from-file=ca.crt=./cloudEventMeshDemoCA.crt --from-file=ca.key=./cloudEventMeshDemoCA.key

```
Now that we have created our CA keypair secret, let's create a certificate manager issuer that uses our CA: 

```
apiVersion: certmanager.k8s.io/v1alpha1
kind: Issuer
metadata:
  name: cloud-native-event-mesh-demo-cert-issuer
  namespace: cloud-event-mesh-demo
spec:
  ca:
    secretName: cloud-native-event-mesh-demo-ca-pair

```
Now 