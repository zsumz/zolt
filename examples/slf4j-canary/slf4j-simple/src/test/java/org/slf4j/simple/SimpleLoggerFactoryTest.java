package org.slf4j.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleLoggerFactoryTest {
    @Test
    public void returnsSameLoggerForName() {
        SimpleLoggerFactory factory = new SimpleLoggerFactory();

        Logger first = factory.getLogger("canary");
        Logger second = factory.getLogger("canary");

        assertSame(first, second);
        assertEquals("canary", first.getName());
    }

    @Test
    public void capturesLogEvents() {
        SimpleLogger logger = new SimpleLoggerFactory().getSimpleLogger("events");

        logger.info("started");
        logger.debug(System.getProperty("slf4j.canary.mode", "debugged"));

        assertEquals("INFO events - started", logger.events().get(0));
        assertEquals("DEBUG events - workspace", logger.events().get(1));
    }

    @Test
    public void installsProviderIntoApiFacade() {
        SimpleLoggerFactory factory = new SimpleLoggerFactory();
        LoggerFactory.setProvider(factory);

        Logger logger = LoggerFactory.getLogger(SimpleLoggerFactoryTest.class);
        logger.info("facade");

        assertEquals(SimpleLoggerFactoryTest.class.getName(), logger.getName());
        assertEquals("INFO " + SimpleLoggerFactoryTest.class.getName() + " - facade",
                factory.getSimpleLogger(SimpleLoggerFactoryTest.class.getName()).events().get(0));
    }
}
