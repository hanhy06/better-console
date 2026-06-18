package io.github.hanhy06.betterconsole.console;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CommandCompleter implements Completer {
    private static final int COMPLETION_TIMEOUT_SECONDS = 2;

    private volatile DedicatedServer server;

    public void attach(DedicatedServer server) {
        this.server = server;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        DedicatedServer server = this.server;
        if (server == null) return;

        Suggestions suggestions = getSuggestions(server, line.line(), line.cursor());
        if (suggestions == null) return;

        int wordCursor = line.wordCursor();
        int wordLength = line.word().length();
        if (line instanceof CompletingParsedLine completingLine) {
            wordCursor = completingLine.rawWordCursor();
            wordLength = completingLine.rawWordLength();
        }

        int wordStart = line.cursor() - wordCursor;
        int wordEnd = wordStart + wordLength;
        for (Suggestion suggestion : suggestions.getList()) {
            int start = suggestion.getRange().getStart();
            int end = suggestion.getRange().getEnd();
            if (start < wordStart || end > wordEnd) continue;

            String value = line.line().substring(wordStart, start)
                    + suggestion.getText()
                    + line.line().substring(end, wordEnd);
            String description = suggestion.getTooltip() == null ? null : suggestion.getTooltip().getString();
            candidates.add(new Candidate(value, suggestion.getText(), null, description, null, null, true));
        }
    }

    private Suggestions getSuggestions(DedicatedServer server, String input, int cursor) {
        CompletableFuture<Suggestions> future = new CompletableFuture<>();
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack();
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            ParseResults<CommandSourceStack> parse = dispatcher.parse(input, source);

            dispatcher.getCompletionSuggestions(parse, cursor).whenComplete((suggestions, throwable) -> {
                if (throwable == null) {
                    future.complete(suggestions);
                } else {
                    future.completeExceptionally(throwable);
                }
            });
        });

        try {
            return future.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
