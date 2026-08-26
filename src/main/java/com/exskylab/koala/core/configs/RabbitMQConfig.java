package com.exskylab.koala.core.configs;


import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.StatefulRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    public static final String EMAIL_QUEUE = "email.queue";
    public static final String EMAIL_ROUTING_KEY = "notification.email";

    public static final String SMS_QUEUE = "sms.queue";
    public static final String SMS_ROUTING_KEY = "notification.sms";

    public static final String PUSH_QUEUE = "push.queue";
    public static final String PUSH_ROUTING_KEY = "notification.push";

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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setChannelTransacted(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter());
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
            StatefulRetryOperationsInterceptor iyzicoRetryInterceptor) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter());

        factory.setAdviceChain(iyzicoRetryInterceptor);

        return factory;
    }



}
