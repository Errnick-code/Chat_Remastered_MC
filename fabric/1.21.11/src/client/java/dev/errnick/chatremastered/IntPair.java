package dev.errnick.chatremastered;

public record IntPair(Integer first, Integer second) {

    public Integer getFirst() {
        return first;
    }

    public Integer getSecond() {
        return second;
    }
}
