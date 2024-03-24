package org.springframework.kafka.annotation;

import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ParallelConsumerImportSelector;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ParallelConsumerImportSelector.class)
public @interface EnableParallelConsumer {
}
