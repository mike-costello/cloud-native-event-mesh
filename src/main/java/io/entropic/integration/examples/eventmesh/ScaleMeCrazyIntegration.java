package io.entropic.integration.examples.eventmesh;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class ScaleMeCrazyIntegration extends RouteBuilder {

	@Override
	public void configure() throws Exception {
		from("knative:channel/scale-me-crazy")
			.log("Message in ${headers}")
			.log("With Body ${body}")
			.process(new Processor() {

				public void process(Exchange exchange) throws Exception {
					/**
					 * @author mcostell
					 * Putting the integration to sleep as it will not be able to 
					 * keep up with its producer and force scaling of the integration 
					 */
					Thread.sleep(3000);
					
				}
				
			}).log("Scaling on the bus!!!");

	}

}
