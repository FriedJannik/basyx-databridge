/*******************************************************************************
 * Copyright (C) 2024 the Eclipse BaSyx Authors
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * SPDX-License-Identifier: MIT
 ******************************************************************************/
package org.eclipse.digitaltwin.basyx.databridge.examples.httppollingauthjsonataaas.test;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.basyx.databridge.aas.configuration.factory.AASProducerDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.component.DataBridgeComponent;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.factory.RoutesConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.core.configuration.route.core.RoutesConfiguration;
import org.eclipse.digitaltwin.basyx.databridge.httppolling.configuration.factory.HttpPollingDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.jsonata.configuration.factory.JsonataDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.databridge.timer.configuration.factory.TimerDefaultConfigurationFactory;
import org.eclipse.digitaltwin.basyx.submodelrepository.SubmodelRepository;
import org.eclipse.digitaltwin.basyx.submodelrepository.component.SubmodelRepositoryComponent;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Test class to test access to a secured Endpoint
 *
 * @author zielstor, fried
 */
public class TestAASUpdater {
	private static final String PATH_PROPERTY_1 = "http://127.0.0.1:8070/value1";
	private static final String PATH_PROPERTY_2 = "http://127.0.0.1:8070/value2";
	private static final String ENDPOINT_1 = "http://localhost:4001/submodels/aHR0cHM6Ly9leGFtcGxlLmNvbS9pZHMvU3VibW9kZWwvMzE3NV80MTMxXzc2MzFfMzQ5Mw/submodel-elements/intProperty";
	private static final String ENDPOINT_2 = "http://localhost:4001/submodels/aHR0cHM6Ly9leGFtcGxlLmNvbS9pZHMvU3VibW9kZWwvMzE3NV80MTMxXzc2MzFfMzQ5Mw/submodel-elements/stringProperty";
	private static final String BAERER_TOKEN = "AbCdEf123456";
	private static final String CONSUMER1_RESULT = "[{\"value\":\"10\"}]";
	private static final String CONSUMER2_RESULT = "[{\"value\":\"Test\"}]";
	private static final String PRODUCER1_BODY = "{\"intProperty\": \"10\"}";
	private static final String PRODUCER2_BODY = "{\"stringProperty\": \"Test\"}";

	protected static String INT_PROPERTY = "";
	protected static String STRING_PROPERTY = "";

	private static DataBridgeComponent updater;
	private static ConfigurableApplicationContext appContext;

	@BeforeClass
	public static void setUp() throws IOException {
		//createAndStartSubmodelRepository();
		createAndStartMockConsumer();
	}

	@Test
	public void test() throws Exception {
		System.out.println("START UPDATER");
		ClassLoader loader = TestAASUpdater.class.getClassLoader();
		RoutesConfiguration configuration = new RoutesConfiguration();

		// Extend configutation for connections
		// DefaulRoutesConfigFac takes default routes.json as to config
		RoutesConfigurationFactory routesFactory = new RoutesConfigurationFactory(loader);
		configuration.addRoutes(routesFactory.create());

		// Extend configuration for Http Source
		HttpPollingDefaultConfigurationFactory httpPollingConfigFactory = new HttpPollingDefaultConfigurationFactory(loader);
		configuration.addDatasources(httpPollingConfigFactory.create());

		// Extend configuration for AAS
		// DefaulRoutesConfigFactory takes default aasserver.json as to config
		AASProducerDefaultConfigurationFactory aasConfigFactory = new AASProducerDefaultConfigurationFactory(loader);
		configuration.addDatasinks(aasConfigFactory.create());

		// Extend configuration for Jsonata
		JsonataDefaultConfigurationFactory jsonataConfigFactory = new JsonataDefaultConfigurationFactory(loader);
		configuration.addTransformers(jsonataConfigFactory.create());

		// Extend configuration for Timer
		TimerDefaultConfigurationFactory timerConfigFactory = new TimerDefaultConfigurationFactory(loader);
		configuration.addDatasources(timerConfigFactory.create());

		updater = new DataBridgeComponent(configuration);
		updater.startComponent();
		System.out.println("UPDATER STARTED");
		waitForPropagation();
		checkIfPropertyIsUpdated();
		updater.stopComponent();
	}

	private static void createAndStartMockConsumer() {
		ClientAndServer clientServer = ClientAndServer.startClientAndServer(8070);

		System.out.println("MockProducer running: " + clientServer.isRunning());

		clientServer.when(
						HttpRequest.request(PATH_PROPERTY_1)
								.withMethod("GET")
								.withHeader("Authorization")
				)
				.respond(
						HttpResponse.response()
								.withStatusCode(HttpStatusCode.OK_200.code())
								.withBody(CONSUMER1_RESULT)
				);

		clientServer.when(
						HttpRequest.request(PATH_PROPERTY_2)
								.withMethod("GET")
								.withHeader("Authorization")
				)
				.respond(
						HttpResponse.response()
								.withStatusCode(HttpStatusCode.OK_200.code())
								.withBody(CONSUMER2_RESULT)
				);
		clientServer.when(
						HttpRequest.request(PATH_PROPERTY_2)
								.withMethod("GET")
				)
				.respond(
						HttpResponse.response()
								.withStatusCode(HttpStatusCode.UNAUTHORIZED_401.code())
				);
	}

	private HttpResponse storeIntValue(String intValue){
		INT_PROPERTY = intValue;
		return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code());
	}
	private HttpResponse storeStringValue(String stringValue){
		STRING_PROPERTY = stringValue;
		return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code());
	}

	private void waitForPropagation() throws InterruptedException {
		Thread.sleep(3000);
	}

	private void checkIfPropertyIsUpdated() throws InterruptedException {
		/*Property intProperty = (Property)appContext.getBean(SubmodelRepository.class).getSubmodelElement("https://example.com/ids/Submodel/3175_4131_7631_3493","intProperty");
		Property stringProperty = (Property)appContext.getBean(SubmodelRepository.class).getSubmodelElement("https://example.com/ids/Submodel/3175_4131_7631_3493","stringProperty");
		assertEquals("10",intProperty.getValue());
		assertEquals("Test",stringProperty.getValue());*/
	}
}
