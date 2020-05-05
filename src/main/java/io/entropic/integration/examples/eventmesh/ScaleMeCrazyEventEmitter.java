package io.entropic.integration.examples.eventmesh;

import org.apache.camel.builder.RouteBuilder;

public class ScaleMeCrazyEventEmitter extends RouteBuilder {

	@Override
	public void configure() throws Exception {
		from("timer:scaleItUp?period=50")
			.log("pumping the autoscaler")
			.transform().simple("Demo'ing autoscaling")
			.to("knative:channel/scale-me-crazy");

	}

}
