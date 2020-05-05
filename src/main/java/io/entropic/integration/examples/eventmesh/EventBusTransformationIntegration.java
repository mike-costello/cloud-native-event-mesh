package io.entropic.integration.examples.eventmesh;

import org.apache.camel.builder.RouteBuilder;

public class EventBusTransformationIntegration extends RouteBuilder {

	@Override
	public void configure() throws Exception{
		
		from("knative:channel/testing-dbevents")
		.log("**Received message ${body}**")
		.setBody().simple("${body} processed after consumed by the event bus. Get on the bus!")
		.to("knative:channel/testing-dbeventaggregate")
		.log("**sent message to knative channel ${body} **");
		
	}
}
