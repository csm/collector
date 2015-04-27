package com.graylog.agent.cli;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.graylog.agent.cli.commands.AgentCommand;
import com.graylog.agent.cli.commands.AgentHelp;
import com.graylog.agent.cli.commands.Server;
import com.graylog.agent.cli.commands.Version;
import io.airlift.airline.Cli;
import io.airlift.airline.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static AgentCommand command = null;

    public static void main(String[] args) {
        final Injector injector = Guice.createInjector();

        final Cli.CliBuilder<AgentCommand> cliBuilder = Cli.<AgentCommand>builder("graylog-agent")
                .withDescription("Graylog agent")
                .withDefaultCommand(AgentHelp.class)
                .withCommand(AgentHelp.class)
                .withCommand(Version.class)
                .withCommand(Server.class);

        final Cli<AgentCommand> cli = cliBuilder.build();

        try {
            command = cli.parse(args);
            command.run();
        } catch (ParseException e) {
            LOG.error(e.getMessage());
            LOG.error("Exit");
        }
    }

    public static void stop(String[] args) {
        if (command != null) {
            command.stop();
        }
    }
}
