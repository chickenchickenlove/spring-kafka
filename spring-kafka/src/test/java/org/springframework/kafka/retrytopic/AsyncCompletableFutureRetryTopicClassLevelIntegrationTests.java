/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.kafka.retrytopic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.GenericMessageConverter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.ReflectionUtils;

/**
 * @author Sanghyeok An
 * @since 3.3.0
 */

@SpringJUnitConfig
@DirtiesContext
@EmbeddedKafka
@TestPropertySource(properties = { "five.attempts=5", "kafka.template=customKafkaTemplate"})
public class AsyncCompletableFutureRetryTopicClassLevelIntegrationTests {

	public final static String FIRST_TOPIC = "myRetryTopic1";

	public final static String SECOND_TOPIC = "myRetryTopic2";

	public final static String THIRD_TOPIC = "myRetryTopic3";

	public final static String FOURTH_TOPIC = "myRetryTopic4";

	public final static String TWO_LISTENERS_TOPIC = "myRetryTopic5";

	public final static String MANUAL_TOPIC = "myRetryTopic6";

	public final static String NOT_RETRYABLE_EXCEPTION_TOPIC = "noRetryTopic";

	public final static String FIRST_REUSE_RETRY_TOPIC = "reuseRetry1";

	public final static String SECOND_REUSE_RETRY_TOPIC = "reuseRetry2";

	public final static String THIRD_REUSE_RETRY_TOPIC = "reuseRetry3";

	private final static String MAIN_TOPIC_CONTAINER_FACTORY = "kafkaListenerContainerFactory";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private CountDownLatchContainer latchContainer;

	@Autowired
	DestinationTopicContainer topicContainer;

	@Test
	void shouldRetryFirstTopic(@Autowired KafkaListenerEndpointRegistry registry) {
		kafkaTemplate.send(FIRST_TOPIC, "Testing topic 1");
		assertThat(topicContainer.getNextDestinationTopicFor("firstTopicId", FIRST_TOPIC).getDestinationName())
				.isEqualTo("myRetryTopic1-retry");
		assertThat(awaitLatch(latchContainer.countDownLatch1)).isTrue();
		assertThat(awaitLatch(latchContainer.customDltCountdownLatch)).isTrue();
		assertThat(awaitLatch(latchContainer.customErrorHandlerCountdownLatch)).isTrue();
		assertThat(awaitLatch(latchContainer.customMessageConverterCountdownLatch)).isTrue();
		registry.getListenerContainerIds().stream()
				.filter(id -> id.startsWith("first"))
				.forEach(id -> {
					ConcurrentMessageListenerContainer<?, ?> container = (ConcurrentMessageListenerContainer<?, ?>) registry
							.getListenerContainer(id);
					if (id.equals("firstTopicId")) {
						assertThat(container.getConcurrency()).isEqualTo(2);
					}
					else {
						assertThat(container.getConcurrency())
								.describedAs("Expected %s to have concurrency", id)
								.isEqualTo(1);
					}
				});
	}

	@Test
	void shouldRetrySecondTopic() {
		kafkaTemplate.send(SECOND_TOPIC, "Testing topic 2");
		assertThat(awaitLatch(latchContainer.countDownLatch2)).isTrue();
		assertThat(awaitLatch(latchContainer.customDltCountdownLatch)).isTrue();
	}

	@Test
	void shouldRetryThirdTopicWithTimeout(
			@Autowired KafkaAdmin admin,
			@Autowired KafkaListenerEndpointRegistry registry) throws Exception {

		kafkaTemplate.send(THIRD_TOPIC, "Testing topic 3");
		assertThat(awaitLatch(latchContainer.countDownLatch3)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatchDltOne)).isTrue();
		Map<String, TopicDescription> topics = admin.describeTopics(THIRD_TOPIC, THIRD_TOPIC + "-dlt", FOURTH_TOPIC);
		assertThat(topics.get(THIRD_TOPIC).partitions()).hasSize(2);
		assertThat(topics.get(THIRD_TOPIC + "-dlt").partitions()).hasSize(3);
		assertThat(topics.get(FOURTH_TOPIC).partitions()).hasSize(2);
		AtomicReference<Method> method = new AtomicReference<>();
		ReflectionUtils.doWithMethods(KafkaAdmin.class, m -> {
			m.setAccessible(true);
			method.set(m);
		}, m -> m.getName().equals("newTopics"));
		@SuppressWarnings("unchecked")
		Collection<NewTopic> weededTopics = (Collection<NewTopic>) method.get().invoke(admin);
		AtomicInteger weeded = new AtomicInteger();
		weededTopics.forEach(topic -> {
			if (topic.name().equals(THIRD_TOPIC) || topic.name().equals(FOURTH_TOPIC)) {
				assertThat(topic).isExactlyInstanceOf(NewTopic.class);
				weeded.incrementAndGet();
			}
		});
		assertThat(weeded.get()).isEqualTo(2);
		registry.getListenerContainerIds().stream()
				.filter(id -> id.startsWith("third"))
				.forEach(id -> {
					ConcurrentMessageListenerContainer<?, ?> container =
							(ConcurrentMessageListenerContainer<?, ?>) registry.getListenerContainer(id);
					if (id.equals("thirdTopicId")) {
						assertThat(container.getConcurrency()).isEqualTo(2);
					}
					else {
						assertThat(container.getConcurrency())
								.describedAs("Expected %s to have concurrency", id)
								.isEqualTo(1);
					}
				});
	}

	@Test
	void shouldRetryFourthTopicWithNoDlt() {
		kafkaTemplate.send(FOURTH_TOPIC, "Testing topic 4");
		assertThat(awaitLatch(latchContainer.countDownLatch4)).isTrue();
	}

	@Test
	void shouldRetryFifthTopicWithTwoListenersAndManualAssignment(
			@Autowired FifthTopicListener1 listener1,
			@Autowired FifthTopicListener2 listener2) {

		kafkaTemplate.send(TWO_LISTENERS_TOPIC, 0, "0", "Testing topic 5 - 0");
		kafkaTemplate.send(TWO_LISTENERS_TOPIC, 1, "0", "Testing topic 5 - 1");
		assertThat(awaitLatch(latchContainer.countDownLatch51)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatch52)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatchDltThree)).isTrue();
		assertThat(listener1.topics).containsExactly(
				TWO_LISTENERS_TOPIC,
				TWO_LISTENERS_TOPIC + "-listener1-0",
				TWO_LISTENERS_TOPIC + "-listener1-1",
				TWO_LISTENERS_TOPIC + "-listener1-2",
				TWO_LISTENERS_TOPIC + "-listener1-dlt"
		);
		assertThat(listener2.topics).containsExactly(
				TWO_LISTENERS_TOPIC,
				TWO_LISTENERS_TOPIC + "-listener2-0",
				TWO_LISTENERS_TOPIC + "-listener2-1",
				TWO_LISTENERS_TOPIC + "-listener2-2",
				TWO_LISTENERS_TOPIC + "-listener2-dlt");
	}

	@Test
	void shouldRetryManualTopicWithDefaultDlt(
			@Autowired KafkaListenerEndpointRegistry registry,
			@Autowired ConsumerFactory<String, String> cf) {

		kafkaTemplate.send(MANUAL_TOPIC, "Testing topic 6");
		assertThat(awaitLatch(latchContainer.countDownLatch6)).isTrue();
		registry.getListenerContainerIds().stream()
				.filter(id -> id.startsWith("manual"))
				.forEach(id -> {
					ConcurrentMessageListenerContainer<?, ?> container =
							(ConcurrentMessageListenerContainer<?, ?>) registry.getListenerContainer(id);
					assertThat(container)
							.extracting("commonErrorHandler")
							.extracting("seekAfterError", InstanceOfAssertFactories.BOOLEAN)
							.isFalse();
				});
		Consumer<String, String> consumer = cf.createConsumer("manual-dlt", "");
		Set<org.apache.kafka.common.TopicPartition> tp =
				Set.of(new org.apache.kafka.common.TopicPartition(MANUAL_TOPIC + "-dlt", 0));
		consumer.assign(tp);
		try {
			await().untilAsserted(() -> {
				OffsetAndMetadata offsetAndMetadata = consumer.committed(tp).get(tp.iterator().next());
				assertThat(offsetAndMetadata).isNotNull();
				assertThat(offsetAndMetadata.offset()).isEqualTo(1L);
			});
		}
		finally {
			consumer.close();
		}
	}

	@Test
	void shouldFirstReuseRetryTopic(@Autowired
									FirstReuseRetryTopicListener listener1,
									@Autowired
									SecondReuseRetryTopicListener listener2, @Autowired
									ThirdReuseRetryTopicListener listener3) {

		kafkaTemplate.send(FIRST_REUSE_RETRY_TOPIC, "Testing reuse topic 1");
		kafkaTemplate.send(SECOND_REUSE_RETRY_TOPIC, "Testing reuse topic 2");
		kafkaTemplate.send(THIRD_REUSE_RETRY_TOPIC, "Testing reuse topic 3");
		assertThat(awaitLatch(latchContainer.countDownLatchReuseOne)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatchReuseTwo)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatchReuseThree)).isTrue();
		assertThat(listener1.topics).containsExactly(
				FIRST_REUSE_RETRY_TOPIC,
				FIRST_REUSE_RETRY_TOPIC + "-retry");
		assertThat(listener2.topics).containsExactly(
				SECOND_REUSE_RETRY_TOPIC,
				SECOND_REUSE_RETRY_TOPIC + "-retry-30",
				SECOND_REUSE_RETRY_TOPIC + "-retry-60",
				SECOND_REUSE_RETRY_TOPIC + "-retry-100",
				SECOND_REUSE_RETRY_TOPIC + "-retry-100");
		assertThat(listener3.topics).containsExactly(
				THIRD_REUSE_RETRY_TOPIC,
				THIRD_REUSE_RETRY_TOPIC + "-retry",
				THIRD_REUSE_RETRY_TOPIC + "-retry",
				THIRD_REUSE_RETRY_TOPIC + "-retry",
				THIRD_REUSE_RETRY_TOPIC + "-retry");
	}

	@Test
	void shouldGoStraightToDlt() {
		kafkaTemplate.send(NOT_RETRYABLE_EXCEPTION_TOPIC, "Testing topic with annotation 1");
		assertThat(awaitLatch(latchContainer.countDownLatchNoRetry)).isTrue();
		assertThat(awaitLatch(latchContainer.countDownLatchDltTwo)).isTrue();
	}

	private boolean awaitLatch(CountDownLatch latch) {
		try {
			return latch.await(60, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			fail(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@KafkaListener(
			id = "firstTopicId",
			topics = FIRST_TOPIC,
			containerFactory = MAIN_TOPIC_CONTAINER_FACTORY,
			errorHandler = "myCustomErrorHandler",
			contentTypeConverter = "myCustomMessageConverter",
			concurrency = "2")
	static class FirstTopicListener {

		@Autowired
		DestinationTopicContainer topicContainer;

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listen(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownLatch1.countDown();
				throw new RuntimeException("Woooops... in topic " + receivedTopic);
			});
		}

	}

	@KafkaListener(topics = SECOND_TOPIC, containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class SecondTopicListener {

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listenAgain(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatch2);
				throw new IllegalStateException("Another woooops... " + receivedTopic);
			});
		}
	}

	@RetryableTopic(
			attempts = "${five.attempts}",
			backoff = @Backoff(delay = 250, maxDelay = 1000, multiplier = 1.5),
			numPartitions = "#{3}",
			timeout = "${missing.property:100000}",
			include = MyRetryException.class, kafkaTemplate = "${kafka.template}",
			topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
			concurrency = "1")
	@KafkaListener(
			id = "thirdTopicId",
			topics = THIRD_TOPIC,
			containerFactory = MAIN_TOPIC_CONTAINER_FACTORY,
			concurrency = "2")
	static class ThirdTopicListener {

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listenWithAnnotation(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatch3);
				throw new MyRetryException("Annotated woooops... " + receivedTopic);
			});
		}

		@DltHandler
		public void annotatedDltMethod(Object message) {
			container.countDownLatchDltOne.countDown();
		}
	}

	@RetryableTopic(dltStrategy = DltStrategy.NO_DLT, attempts = "4", backoff = @Backoff(300),
			sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
			kafkaTemplate = "${kafka.template}")
	@KafkaListener(topics = FOURTH_TOPIC, containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class FourthTopicListener {

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listenNoDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatch4);
				throw new IllegalStateException("Another woooops... " + receivedTopic);
			});
		}

		@DltHandler
		public void shouldNotGetHere() {
			fail("Dlt should not be processed!");
		}
	}

	static class AbstractFifthTopicListener {

		final List<String> topics = Collections.synchronizedList(new ArrayList<>());

		@Autowired
		CountDownLatchContainer container;

		@DltHandler
		public void annotatedDltMethod(ConsumerRecord<?, ?> record) {
			this.topics.add(record.topic());
			container.countDownLatchDltThree.countDown();
		}

	}

	@RetryableTopic(
			attempts = "4",
			backoff = @Backoff(250),
			numPartitions = "2",
			retryTopicSuffix = "-listener1",
			dltTopicSuffix = "-listener1-dlt",
			topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
			sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
			kafkaTemplate = "${kafka.template}")
	@KafkaListener(
			id = "fifthTopicId1",
			topicPartitions = {@org.springframework.kafka.annotation.TopicPartition(topic = TWO_LISTENERS_TOPIC,
			partitionOffsets = @PartitionOffset(partition = "0", initialOffset = "0"))},
			containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class FifthTopicListener1 extends AbstractFifthTopicListener {

		@KafkaHandler
		public CompletableFuture<Void> listenWithAnnotation(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			this.topics.add(receivedTopic);
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatch51);
				throw new RuntimeException("Annotated woooops... " + receivedTopic);
			});
		}

	}

	@RetryableTopic(
			attempts = "4",
			backoff = @Backoff(250),
			numPartitions = "2",
			retryTopicSuffix = "-listener2",
			dltTopicSuffix = "-listener2-dlt",
			topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
			sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
			kafkaTemplate = "${kafka.template}")
	@KafkaListener(
			id = "fifthTopicId2",
			topicPartitions = {@org.springframework.kafka.annotation.TopicPartition(topic = TWO_LISTENERS_TOPIC,
			partitionOffsets = @PartitionOffset(partition = "1", initialOffset = "0"))},
			containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class FifthTopicListener2 extends AbstractFifthTopicListener {

		@KafkaHandler
		public CompletableFuture<Void> listenWithAnnotation2(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			this.topics.add(receivedTopic);
			return CompletableFuture.supplyAsync(() -> {
				container.countDownLatch52.countDown();
				throw new RuntimeException("Annotated woooops... " + receivedTopic);
			});
		}

	}

	@RetryableTopic(
			attempts = "4",
			backoff = @Backoff(50),
			sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS)
	@KafkaListener(
			id = "manual",
			topics = MANUAL_TOPIC,
			containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class SixthTopicDefaultDLTListener {

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listenNoDlt(
				String message,
				@Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic,
				@SuppressWarnings("unused") Acknowledgment ack) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatch6);
				throw new IllegalStateException("Another woooops... " + receivedTopic);
			});
		}

	}

	@RetryableTopic(
			attempts = "3",
			numPartitions = "3",
			exclude = MyDontRetryException.class,
			backoff = @Backoff(delay = 50, maxDelay = 100, multiplier = 3),
			traversingCauses = "true",
			kafkaTemplate = "${kafka.template}")
	@KafkaListener(topics = NOT_RETRYABLE_EXCEPTION_TOPIC, containerFactory = MAIN_TOPIC_CONTAINER_FACTORY)
	static class NoRetryTopicListener {

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Object> listenWithAnnotation2(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			return CompletableFuture.supplyAsync(() -> {
				container.countDownIfNotKnown(receivedTopic, container.countDownLatchNoRetry);
				throw new MyDontRetryException("Annotated second woooops... " + receivedTopic);
			});
		}

		@DltHandler
		public void annotatedDltMethod(Object message) {
			container.countDownLatchDltTwo.countDown();
		}
	}

	@RetryableTopic(
			attempts = "2",
			backoff = @Backoff(50))
	@KafkaListener(
			id = "reuseRetry1",
			topics = FIRST_REUSE_RETRY_TOPIC,
			containerFactory = "retryTopicListenerContainerFactory")
	static class FirstReuseRetryTopicListener {

		final List<String> topics = Collections.synchronizedList(new ArrayList<>());

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listen1(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			this.topics.add(receivedTopic);
			return CompletableFuture.supplyAsync(() -> {
				container.countDownLatchReuseOne.countDown();
				throw new RuntimeException("Another woooops... " + receivedTopic);
			});
		}

	}

	@RetryableTopic(
			attempts = "5",
			backoff = @Backoff(delay = 30, maxDelay = 100, multiplier = 2))
	@KafkaListener(
			id = "reuseRetry2",
			topics = SECOND_REUSE_RETRY_TOPIC,
			containerFactory = "retryTopicListenerContainerFactory")
	static class SecondReuseRetryTopicListener {

		final List<String> topics = Collections.synchronizedList(new ArrayList<>());

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listen2(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			this.topics.add(receivedTopic);
			return CompletableFuture.supplyAsync(() -> {
				container.countDownLatchReuseTwo.countDown();
				throw new RuntimeException("Another woooops... " + receivedTopic);
			});
		}

	}

	@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1, maxDelay = 5, multiplier = 1.4))
	@KafkaListener(id = "reuseRetry3", topics = THIRD_REUSE_RETRY_TOPIC,
			containerFactory = "retryTopicListenerContainerFactory")
	static class ThirdReuseRetryTopicListener {

		final List<String> topics = Collections.synchronizedList(new ArrayList<>());

		@Autowired
		CountDownLatchContainer container;

		@KafkaHandler
		public CompletableFuture<Void> listen3(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
			this.topics.add(receivedTopic);
			return CompletableFuture.supplyAsync(() -> {
				container.countDownLatchReuseThree.countDown();
				throw new RuntimeException("Another woooops... " + receivedTopic);
			});

		}

	}

	static class CountDownLatchContainer {

		CountDownLatch countDownLatch1 = new CountDownLatch(5);

		CountDownLatch countDownLatch2 = new CountDownLatch(3);

		CountDownLatch countDownLatch3 = new CountDownLatch(3);

		CountDownLatch countDownLatch4 = new CountDownLatch(4);

		CountDownLatch countDownLatch51 = new CountDownLatch(4);

		CountDownLatch countDownLatch52 = new CountDownLatch(4);

		CountDownLatch countDownLatch6 = new CountDownLatch(4);

		CountDownLatch countDownLatchNoRetry = new CountDownLatch(1);

		CountDownLatch countDownLatchDltOne = new CountDownLatch(1);

		CountDownLatch countDownLatchDltTwo = new CountDownLatch(1);

		CountDownLatch countDownLatchDltThree = new CountDownLatch(2);

		CountDownLatch countDownLatchReuseOne = new CountDownLatch(2);

		CountDownLatch countDownLatchReuseTwo = new CountDownLatch(5);

		CountDownLatch countDownLatchReuseThree = new CountDownLatch(5);

		CountDownLatch customDltCountdownLatch = new CountDownLatch(1);

		CountDownLatch customErrorHandlerCountdownLatch = new CountDownLatch(6);

		CountDownLatch customMessageConverterCountdownLatch = new CountDownLatch(6);

		final List<String> knownTopics = new ArrayList<>();

		private void countDownIfNotKnown(String receivedTopic, CountDownLatch countDownLatch) {
			synchronized (knownTopics) {
				if (!knownTopics.contains(receivedTopic)) {
					knownTopics.add(receivedTopic);
					countDownLatch.countDown();
				}
			}
		}
	}

	static class MyCustomDltProcessor {

		@Autowired
		KafkaTemplate<String, String> kafkaTemplate;

		@Autowired
		CountDownLatchContainer container;

		public void processDltMessage(Object message) {
			container.customDltCountdownLatch.countDown();
			throw new RuntimeException("Dlt Error!");
		}
	}

	@SuppressWarnings("serial")
	static class MyRetryException extends RuntimeException {
		MyRetryException(String msg) {
			super(msg);
		}
	}

	@SuppressWarnings("serial")
	static class MyDontRetryException extends RuntimeException {
		MyDontRetryException(String msg) {
			super(msg);
		}
	}

	@Configuration
	static class RetryTopicConfigurations extends RetryTopicConfigurationSupport {

		private static final String DLT_METHOD_NAME = "processDltMessage";

		@Bean
		RetryTopicConfiguration firstRetryTopic(KafkaTemplate<String, String> template) {
			return RetryTopicConfigurationBuilder
					.newInstance()
					.fixedBackOff(50)
					.maxAttempts(5)
					.concurrency(1)
					.useSingleTopicForSameIntervals()
					.includeTopic(FIRST_TOPIC)
					.doNotRetryOnDltFailure()
					.dltHandlerMethod("myCustomDltProcessor", DLT_METHOD_NAME)
					.create(template);
		}

		@Bean
		RetryTopicConfiguration secondRetryTopic(KafkaTemplate<String, String> template) {
			return RetryTopicConfigurationBuilder
					.newInstance()
					.exponentialBackoff(500, 2, 10000)
					.retryOn(Arrays.asList(IllegalStateException.class, IllegalAccessException.class))
					.traversingCauses()
					.includeTopic(SECOND_TOPIC)
					.doNotRetryOnDltFailure()
					.dltHandlerMethod("myCustomDltProcessor", DLT_METHOD_NAME)
					.create(template);
		}

		@Bean
		FirstTopicListener firstTopicListener() {
			return new FirstTopicListener();
		}

		@Bean
		KafkaListenerErrorHandler myCustomErrorHandler(
				CountDownLatchContainer container) {
			return (message, exception) -> {
				container.customErrorHandlerCountdownLatch.countDown();
				throw exception;
			};
		}

		@Bean
		SmartMessageConverter myCustomMessageConverter(
				CountDownLatchContainer container) {
			return new CompositeMessageConverter(Collections.singletonList(new GenericMessageConverter())) {

				@Override
				public Object fromMessage(Message<?> message, Class<?> targetClass, Object conversionHint) {
					container.customMessageConverterCountdownLatch.countDown();
					return super.fromMessage(message, targetClass, conversionHint);
				}
			};
		}

		@Bean
		SecondTopicListener secondTopicListener() {
			return new SecondTopicListener();
		}

		@Bean
		ThirdTopicListener thirdTopicListener() {
			return new ThirdTopicListener();
		}

		@Bean
		FourthTopicListener fourthTopicListener() {
			return new FourthTopicListener();
		}

		@Bean
		FifthTopicListener1 fifthTopicListener1() {
			return new FifthTopicListener1();
		}

		@Bean
		FifthTopicListener2 fifthTopicListener2() {
			return new FifthTopicListener2();
		}

		@Bean
		SixthTopicDefaultDLTListener manualTopicListener() {
			return new SixthTopicDefaultDLTListener();
		}

		@Bean
		NoRetryTopicListener noRetryTopicListener() {
			return new NoRetryTopicListener();
		}

		@Bean
		FirstReuseRetryTopicListener firstReuseRetryTopicListener() {
			return new FirstReuseRetryTopicListener();
		}

		@Bean
		SecondReuseRetryTopicListener secondReuseRetryTopicListener() {
			return new SecondReuseRetryTopicListener();
		}

		@Bean
		ThirdReuseRetryTopicListener thirdReuseRetryTopicListener() {
			return new ThirdReuseRetryTopicListener();
		}

		@Bean
		CountDownLatchContainer latchContainer() {
			return new CountDownLatchContainer();
		}

		@Bean
		MyCustomDltProcessor myCustomDltProcessor() {
			return new MyCustomDltProcessor();
		}
	}

	@Configuration
	static class KafkaProducerConfig {

		@Autowired
		EmbeddedKafkaBroker broker;

		@Bean
		ProducerFactory<String, String> producerFactory() {
			Map<String, Object> props = KafkaTestUtils.producerProps(
					this.broker.getBrokersAsString());
			props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			return new DefaultKafkaProducerFactory<>(props);
		}

		@Bean("customKafkaTemplate")
		KafkaTemplate<String, String> kafkaTemplate() {
			return new KafkaTemplate<>(producerFactory());
		}
	}

	@EnableKafka
	@Configuration
	static class KafkaConsumerConfig {

		@Autowired
		EmbeddedKafkaBroker broker;

		@Bean
		KafkaAdmin kafkaAdmin() {
			Map<String, Object> configs = new HashMap<>();
			configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, this.broker.getBrokersAsString());
			return new KafkaAdmin(configs);
		}

		@Bean
		NewTopic topic() {
			return TopicBuilder.name(THIRD_TOPIC).partitions(2).replicas(1).build();
		}

		@Bean
		NewTopics topics() {
			return new NewTopics(TopicBuilder.name(FOURTH_TOPIC).partitions(2).replicas(1).build());
		}

		@Bean
		ConsumerFactory<String, String> consumerFactory() {
			Map<String, Object> props = KafkaTestUtils.consumerProps(
					this.broker.getBrokersAsString(),
					"groupId",
					"false");
			props.put(
					ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
					StringDeserializer.class);
			return new DefaultKafkaConsumerFactory<>(props);
		}

		@Bean
		ConcurrentKafkaListenerContainerFactory<String, String> retryTopicListenerContainerFactory(
				ConsumerFactory<String, String> consumerFactory) {

			ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
			ContainerProperties props = factory.getContainerProperties();
			props.setIdleEventInterval(100L);
			props.setPollTimeout(50L);
			props.setIdlePartitionEventInterval(100L);
			factory.setConsumerFactory(consumerFactory);
			factory.setConcurrency(1);
			factory.setContainerCustomizer(
					container -> container.getContainerProperties().setIdlePartitionEventInterval(100L));
			return factory;
		}

		@Bean
		ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
				ConsumerFactory<String, String> consumerFactory) {

			ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
			factory.setConsumerFactory(consumerFactory);
			factory.setConcurrency(1);
			factory.setContainerCustomizer(container -> {
				if (container.getListenerId().startsWith("manual")) {
					container.getContainerProperties().setAckMode(AckMode.MANUAL);
					container.getContainerProperties().setAsyncAcks(true);
				}
			});
			return factory;
		}

		@Bean
		TaskScheduler sched() {
			return new ThreadPoolTaskScheduler();
		}

	}

}
