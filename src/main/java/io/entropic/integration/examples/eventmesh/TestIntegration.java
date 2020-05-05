package io.entropic.integration.examples.eventmesh;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;

public class TestIntegration extends RouteBuilder {

	@BindToRegistry
	public javax.jms.ConnectionFactory connectionFactory() {
		return new org.apache.qpid.jms.JmsConnectionFactory(
				"amqp://cloud1-router-mesh.cloud-1:5672?transport.trustAll=true&transport.verifyHost=false");
	}
	
	@Override
	public void configure() throws Exception {
		from("timer:tick?period=2s")
		.setBody().simple("test message")
		.log("sending to event mesh msg: ${body}")
		.to("amqp:queue:test.db-events")
		.log("sent an amqp message"); 
	

	}

}
