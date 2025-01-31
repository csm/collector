/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.collector;

import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MessageBuilderTest {
    @Rule
    public ExpectedException throwing = ExpectedException.none();

    @Test
    public void testSuccessfulBuild() throws Exception {
        final DateTime time = DateTime.now();
        final MessageFields fields = new MessageFields();

        fields.put("hello", "world");

        final Message message = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(time)
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(fields)
                .build();

        assertEquals("the message", message.getMessage());
        assertEquals("source", message.getSource());
        assertEquals(time, message.getTimestamp());
        assertEquals(Message.Level.INFO, message.getLevel());
        assertEquals("input-id", message.getInput());
        assertEquals(Sets.newHashSet("output1", "output2"), message.getOutputs());
        assertEquals(new HashMap<String, Object>() {
            {
                put("hello", "world");
            }
        }, message.getFields().asMap());
    }

    @Test
    public void testAddField() throws Exception {
        final DateTime time = DateTime.now();
        final MessageFields fields = new MessageFields();

        fields.put("hello", "world");

        final MessageBuilder builder = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(time)
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(fields);

        Message message = builder.build();

        assertEquals("world", message.getFields().asMap().get("hello"));

        builder.addField("hello", "changed");
        builder.addField("int", 123);
        builder.addField("long", 1000L);
        builder.addField("boolean", true);

        message = builder.build();

        assertEquals("changed", message.getFields().asMap().get("hello"));
        assertEquals(123, message.getFields().asMap().get("int"));
        assertEquals(1000L, message.getFields().asMap().get("long"));
        assertTrue((boolean) message.getFields().asMap().get("boolean"));
    }

    @Test
    public void testEmpty() throws Exception {
        throwing.expect(NullPointerException.class);
        new MessageBuilder().build();
    }

    @Test
    public void testValidations() throws Exception {
        final MessageBuilder builder = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(DateTime.now())
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(new MessageFields());

        builder.message(null);
        ensureFailure(builder, "message");
        builder.message("the message");

        builder.source(null);
        ensureFailure(builder, "source");
        builder.source("source");

        builder.timestamp(null);
        ensureFailure(builder, "timestamp");
        builder.timestamp(DateTime.now());

        builder.input(null);
        ensureFailure(builder, "input");
        builder.input("input-id");

        builder.outputs(null);
        ensureFailure(builder, "outputs");
        builder.outputs(Sets.newHashSet("output1", "output2"));

        builder.build();
    }

    @Test
    public void testCopy() throws Exception {
        final MessageBuilder builder = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(DateTime.now())
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(new MessageFields());
        final MessageBuilder copy = builder.copy();

        assertNotEquals(builder, copy);
    }

    @Test
    public void testThreadAwareness() throws Exception {
        // The builder should throw an error if modified by a different thread than the owning one.
        final MessageBuilder builder = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(DateTime.now())
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(new MessageFields());

        builder.message("modified by owner thread");
        modifyInThread("message", new Runnable() {
            @Override
            public void run() {
                builder.message("modified by another thread");
            }
        });

        builder.source("modified by owner thread");
        modifyInThread("source", new Runnable() {
            @Override
            public void run() {
                builder.source("modified by another thread");
            }
        });

        builder.timestamp(DateTime.now());
        modifyInThread("timestamp", new Runnable() {
            @Override
            public void run() {
                builder.timestamp(DateTime.now());
            }
        });

        builder.level(Message.Level.INFO);
        modifyInThread("level", new Runnable() {
            @Override
            public void run() {
                builder.level(Message.Level.CRITICAL);
            }
        });

        builder.input("modified by owner thread");
        modifyInThread("input", new Runnable() {
            @Override
            public void run() {
                builder.input("modified by another thread");
            }
        });

        builder.outputs(Sets.newHashSet("modified by owner thread"));
        modifyInThread("outputs", new Runnable() {
            @Override
            public void run() {
                builder.outputs(Sets.newHashSet("modified by another thread"));
            }
        });

        builder.fields(new MessageFields());
        modifyInThread("fields", new Runnable() {
            @Override
            public void run() {
                builder.fields(new MessageFields());
            }
        });
    }

    @Test
    public void testCopyCanBeModifiedInThread() throws Exception {
        final MessageBuilder builder = new MessageBuilder()
                .message("the message")
                .source("source")
                .timestamp(DateTime.now())
                .level(Message.Level.INFO)
                .input("input-id")
                .outputs(Sets.newHashSet("output1", "output2"))
                .fields(new MessageFields());

        builder.message("modified by owner thread");

        final AtomicBoolean failed = new AtomicBoolean(true);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    builder.copy().message("modified by another thread");
                    failed.set(false);
                } catch (IllegalStateException ignored) {
                }
            }
        });

        thread.start();
        thread.join();

        assertFalse("Modifying builder copy in another thread should have failed!", failed.get());
    }

    private void modifyInThread(final String field, final Runnable runnable) throws InterruptedException {
        final AtomicBoolean failed = new AtomicBoolean(true);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                    failed.set(false);
                } catch (IllegalStateException ignored) {
                }
            }
        });

        thread.start();
        thread.join();

        assertTrue("Modifying " + field + " in another thread should have failed!", failed.get());
    }

    private void ensureFailure(MessageBuilder builder, String field) {
        try {
            builder.build();
            fail("Builder should fail with " + field + " == null!");
        } catch (Exception ignored) {
        }
    }
}