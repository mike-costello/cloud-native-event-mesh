package io.entropic.integration.examples.eventmesh;

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
