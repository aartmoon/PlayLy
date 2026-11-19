package com.playly.ai.plan;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

public final class GamePlanCli {
    private GamePlanCli() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Pass one quoted game prompt to planGame.");
            System.exit(2);
            return;
        }

        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var model = ChatCompletionsClient.fromEnvironment(http, "planGame");
            var plan = new GamePlanner(model).create(args[0]);
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(plan));
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Game planning was interrupted.");
            System.exit(1);
        }
    }
}
