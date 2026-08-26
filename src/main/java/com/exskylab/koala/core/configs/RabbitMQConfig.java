package com.exskylab.koala.core.configs;


import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.config.StatefulRetryOperationsInterceptorFactoryBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

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
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
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
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(5000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(30000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        StatefulRetryOperationsInterceptorFactoryBean factory = new StatefulRetryOperationsInterceptorFactoryBean();
        factory.setRetryOperations(retryTemplate);
        factory.setMessageRecoverer(new RejectAndDontRequeueRecoverer());

        try {
            return factory.getObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create retry interceptor", e);
        }
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
