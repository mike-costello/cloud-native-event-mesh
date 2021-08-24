package io.entropic.integration.examples.eventmesh;

import org.apache.camel.builder.RouteBuilder;

public class EventSinkIntegration extends RouteBuilder {


	@Override
	public void configure() throws Exception {
		
		from("timer:dbEventsTrigger?repeatCount=50")
			.transform(simple("Summit Sites welcome to Cloud Native Integration!"))
			.log("Event Triggered. Message: ${body}")
			.setBody().simple("${body} processed by EventSinkIntegration")
			.to("knative:channel/testing-dbevents")
			.log("**sent message to knative channel ${body} **");

	}

}
