package com.exskylab.koala.core.configs;


import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.consumers.EmailDeliveryRecoverer;
import com.exskylab.koala.core.consumers.ConfirmedRepublishMessageRecoverer;
import com.exskylab.koala.core.consumers.NotificationDeliveryBusyException;
import com.exskylab.koala.core.consumers.NotificationDeliveryBusyRecoverer;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.core.logging.LogContextListenerAdvice;
import com.exskylab.koala.core.logging.LogContextPublishPostProcessor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.StatefulRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryPolicy;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_ROUTING_KEY = "notification.email";
    public static final String EMAIL_DEAD_LETTER_EXCHANGE = "notification.email.dead-letter.exchange";
    public static final String EMAIL_DEAD_LETTER_QUEUE = "email.queue.dlq";
    public static final String EMAIL_DEAD_LETTER_ROUTING_KEY = "notification.email.dead-letter";

    public static final String SMS_QUEUE = "sms.queue";
    public static final String SMS_ROUTING_KEY = "notification.sms";

    public static final String PUSH_QUEUE = "push.queue";
    public static final String PUSH_ROUTING_KEY = "notification.push";

    public static final String NOTIFICATION_DELIVERY_BUSY_PARKING_EXCHANGE =
            "notification.delivery.busy.parking.exchange";
    public static final String NOTIFICATION_DELIVERY_BUSY_PARKING_QUEUE =
            "notification.delivery.busy.parking.queue";
    public static final String NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY =
            "notification.delivery.busy.parked";

    public static final String USER_EXCHANGE = "user.exchange";
    public static final String USER_PROFILE_UPDATED_FOR_SYNC_QUEUE = "user.profile.updated.for.sync.queue";
    public static final String USER_PROFILE_UPDATED_ROUTING_KEY = "user.profile.updated";




    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue emailQueue(){
        return QueueBuilder.durable(EMAIL_QUEUE).build();
    }

    @Bean
    public DirectExchange emailDeadLetterExchange() {
        return new DirectExchange(EMAIL_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(EMAIL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding emailDeadLetterBinding() {
        return BindingBuilder.bind(emailDeadLetterQueue())
                .to(emailDeadLetterExchange())
                .with(EMAIL_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Queue smsQueue(){
        return QueueBuilder.durable(SMS_QUEUE).build();
    }

    @Bean
    public Queue pushQueue(){
        return QueueBuilder.durable(PUSH_QUEUE).build();
    }

    @Bean
    public Binding emailBinding(){
        return BindingBuilder
                .bind(emailQueue())
                .to(notificationsExchange())
                .with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding smsBinding(){
        return BindingBuilder
                .bind(smsQueue())
                .to(notificationsExchange())
                .with(SMS_ROUTING_KEY);
    }

    @Bean
    public Binding pushBinding(){
        return BindingBuilder
                .bind(pushQueue())
                .to(notificationsExchange())
                .with(PUSH_ROUTING_KEY);
    }

    @Bean
    public DirectExchange notificationDeliveryBusyParkingExchange() {
        return new DirectExchange(NOTIFICATION_DELIVERY_BUSY_PARKING_EXCHANGE);
    }

    @Bean
    public Queue notificationDeliveryBusyParkingQueue() {
        return QueueBuilder.durable(NOTIFICATION_DELIVERY_BUSY_PARKING_QUEUE).build();
    }

    @Bean
    public Binding notificationDeliveryBusyParkingBinding() {
        return BindingBuilder.bind(notificationDeliveryBusyParkingQueue())
                .to(notificationDeliveryBusyParkingExchange())
                .with(NOTIFICATION_DELIVERY_BUSY_PARKING_ROUTING_KEY);
    }

    @Bean
    public TopicExchange usersExchange(){
        return new TopicExchange(USER_EXCHANGE);
    }

    @Bean
    public Queue userProfileUpdatedForSyncQueue(){
        return QueueBuilder.durable(USER_PROFILE_UPDATED_FOR_SYNC_QUEUE).build();
    }

    @Bean
    public Binding userProfileUpdatedBinding(){
        return BindingBuilder
                .bind(userProfileUpdatedForSyncQueue())
                .to(usersExchange())
                .with(USER_PROFILE_UPDATED_ROUTING_KEY);

    }



    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         LogContextPublishPostProcessor logContextPublishPostProcessor) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        template.setBeforePublishPostProcessors(logContextPublishPostProcessor);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            LogContextListenerAdvice logContextListenerAdvice,
            @Qualifier("notificationDeliveryBusyRetryInterceptor")
            StatelessRetryOperationsInterceptor notificationDeliveryBusyRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAdviceChain(logContextListenerAdvice, notificationDeliveryBusyRetryInterceptor);
        return factory;
    }

    @Bean
    public NotificationDeliveryBusyRecoverer notificationDeliveryBusyRecoverer(
            ConfirmedRabbitPublisher publisher) {
        return new NotificationDeliveryBusyRecoverer(publisher);
    }

    @Bean(name = "notificationDeliveryBusyRetryInterceptor")
    public StatelessRetryOperationsInterceptor notificationDeliveryBusyRetryInterceptor(
            NotificationDispatchProperties properties, NotificationDeliveryBusyRecoverer recoverer) {
        RetryPolicy policy = RetryPolicy.builder()
                .includes(NotificationDeliveryBusyException.class)
                .maxRetries(properties.getDeliveryBusyMaxRetries())
                .delay(properties.getDeliveryBusyRetryInterval())
                .build();
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .recoverer(recoverer)
                .build();
    }

    @Bean(name = "emailRetryInterceptor")
    public StatelessRetryOperationsInterceptor emailRetryInterceptor(EmailDeliveryRecoverer recoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1000, 2.0, 5000)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public EmailDeliveryRecoverer emailDeliveryRecoverer(NotificationService notificationService,
                                                          ObjectMapper objectMapper,
                                                          RabbitTemplate rabbitTemplate,
                                                          ConfirmedRabbitPublisher publisher) {
        return new EmailDeliveryRecoverer(notificationService, objectMapper,
                new ConfirmedRepublishMessageRecoverer(rabbitTemplate, publisher,
                        EMAIL_DEAD_LETTER_EXCHANGE, EMAIL_DEAD_LETTER_ROUTING_KEY));
    }

    @Bean(name = "emailListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory emailListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("emailRetryInterceptor") StatelessRetryOperationsInterceptor emailRetryInterceptor,
            @Qualifier("notificationDeliveryBusyRetryInterceptor")
            StatelessRetryOperationsInterceptor notificationDeliveryBusyRetryInterceptor,
            LogContextListenerAdvice logContextListenerAdvice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAdviceChain(logContextListenerAdvice, emailRetryInterceptor,
                notificationDeliveryBusyRetryInterceptor);
        return factory;
    }


    @Bean(name = "iyzicoRetryInterceptor")
    public StatefulRetryOperationsInterceptor iyzicoRetryInterceptor() {
        return RetryInterceptorBuilder.stateful()
                .maxRetries(2)
                .backOffOptions(5000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = "iyzicoListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory iyzicoListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("iyzicoRetryInterceptor") StatefulRetryOperationsInterceptor iyzicoRetryInterceptor,
            LogContextListenerAdvice logContextListenerAdvice) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter());

        factory.setAdviceChain(logContextListenerAdvice, iyzicoRetryInterceptor);

        return factory;
    }



}
