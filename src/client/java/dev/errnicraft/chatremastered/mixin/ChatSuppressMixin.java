package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredStore;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подавляет ванильный chat.type.text дубль если мод уже показал сообщение сам.
 *
 * Ванильное сообщение в 1.21.11:
 *   root (PlainTextContents.EMPTY)
 *     └─ sibling: TranslatableContents("chat.type.text", [nickComp, textComp])
 *
 * Наши сообщения (msgText из addReplyToChat/addImageToChat) содержат только
 * LiteralContents siblings — TranslatableContents у них нет.
 * Поэтому suppression для реплаев применяется ТОЛЬКО к TranslatableContents,
 * а не к plain-text fallback, чтобы не подавлять собственные сообщения мода.
 */
@Mixin(ChatComponent.class)
public class ChatSuppressMixin {

    // Дедупликация: запоминаем stripped-текст последних N сообщений с таймстампом.
    // Окно 50мс — ловим только настоящие дубли одного тика (ванильный + модовый),
    // не подавляем быстрые повторяющиеся сообщения (например от командных блоков).
    private static final java.util.LinkedHashMap<String, Long> recentMessages =
        new java.util.LinkedHashMap<String, Long>(32, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> e) {
                return size() > 64;
            }
        };

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chatremastered$suppressMessage(
            Component message,
            MessageSignature signature,
            GuiMessageTag tag,
            CallbackInfo ci
    ) {
        suppressIfNeeded(message, ci);
    }

    private static void suppressIfNeeded(Component message, CallbackInfo ci) {
        // Ищем TranslatableContents рекурсивно.
        // В 1.21.11 он находится в siblings root-компонента, не в самом root.
        TranslatableContents tc = findTranslatable(message);
        if (tc != null) {
            Object[] args = tc.getArgs();
            if (args.length >= 2) {
                String nick = args[0] instanceof Component c ? stripObjectContents(c) : args[0].toString();
                String body = args[1] instanceof Component c ? stripObjectContents(c) : args[1].toString();
                if (!nick.isBlank()) {
                    // Дедупликация: подавляем точный дубль сообщения игрока в течение 2 секунд.
                    // Применяется ТОЛЬКО к ванильным chat.type.text, не к системным сообщениям.
                    String dedupeKey = nick + "\u0000" + body;
                    long now = System.currentTimeMillis();
                    Long prev = recentMessages.get(dedupeKey);
                    if (prev != null && now - prev < 50) {
                        ci.cancel();
                        return;
                    }
                    recentMessages.put(dedupeKey, now);

                    // shouldSuppressMessage — фото
                    if (ChatRemasteredStore.shouldSuppressMessage(nick, body)) { ci.cancel(); return; }
                    // shouldSuppressReplyMessage — только для TranslatableContents (ванильный формат).
                    // НЕ применяем к plain-text, чтобы не подавлять наши собственные сообщения.
                    if (ChatRemasteredStore.shouldSuppressReplyMessage(nick, body)) { ci.cancel(); return; }
                }
            }
            return; // Это TranslatableContents — дальше не проверяем
        }

        // Fallback для plain-text формата "<Nick> text" (только фото, не реплаи).
        String raw = stripObjectContents(message);
        // Пропускаем наши собственные сообщения (они содержат маркер [📷] или \n-отступы).
        // Это предотвращает самоподавление: markSuppressPhotoMessage вызывается до addImageToChat,
        // поэтому наше "<Nick> caption [📷]\n..." было бы подавлено по caption-совпадению.
        if (raw.contains("[📷]") || raw.contains("\n")) return;
        int angleStart = raw.indexOf('<');
        if (angleStart >= 0) {
            int closeAngle = raw.indexOf("> ", angleStart);
            if (closeAngle > angleStart) {
                String nick = raw.substring(angleStart + 1, closeAngle);
                String body = raw.substring(closeAngle + 2);
                if (!nick.isBlank()) {
                    if (ChatRemasteredStore.shouldSuppressMessage(nick, body)) { ci.cancel(); return; }
                    // shouldSuppressReplyMessage здесь НЕ вызываем — это может быть наше сообщение
                }
            }
        }

        if (ChatRemasteredStore.shouldSuppressMessageFuzzy(raw)) {
            ci.cancel();
        }
    }

    /** Рекурсивно ищет первый TranslatableContents в дереве компонента. */
    private static TranslatableContents findTranslatable(Component component) {
        if (component.getContents() instanceof TranslatableContents tc) return tc;
        for (Component sibling : component.getSiblings()) {
            TranslatableContents found = findTranslatable(sibling);
            if (found != null) return found;
        }
        return null;
    }

    /** Извлекает текст, пропуская ObjectContents (PlayerSprite и др.) */
    private static String stripObjectContents(Component component) {
        StringBuilder sb = new StringBuilder();
        collectText(component, sb);
        return stripHeadPlaceholders(sb.toString());
    }

    private static String stripHeadPlaceholders(String text) {
        return text.replaceAll("\\[[^\\]]*\\s*head\\]", "").trim();
    }

    private static void collectText(Component component, StringBuilder sb) {
        if (component.getContents() instanceof PlainTextContents plain) {
            sb.append(plain.text());
        } else if (component.getContents() instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component c) collectText(c, sb);
                else sb.append(arg);
            }
        }
        // ObjectContents — пропускаем
        for (Component sibling : component.getSiblings()) {
            collectText(sibling, sb);
        }
    }
}
