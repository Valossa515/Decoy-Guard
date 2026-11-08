package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.SilentAlert;
import io.github.valossa515.decoyguard.core.SilentAlertPublisher;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default publisher: writes the alert to the application log.
 * Replace it with a bean that reaches the fraud team or a trusted contact.
 */
public class LoggingSilentAlertPublisher implements SilentAlertPublisher {

    private static final Log LOG = LogFactory.getLog(LoggingSilentAlertPublisher.class);

    @Override
    public void publish(SilentAlert alert) {
        LOG.warn("Duress session activated: trigger=" + alert.trigger() + " raisedAt=" + alert.raisedAt());
    }
}
