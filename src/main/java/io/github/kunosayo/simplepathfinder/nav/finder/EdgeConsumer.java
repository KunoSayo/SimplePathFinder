package io.github.kunosayo.simplepathfinder.nav.finder;

import io.github.kunosayo.simplepathfinder.nav.NavLinkType;
import io.github.kunosayo.simplepathfinder.nav.layered.AbstractLayeredNavChunk;
import io.github.kunosayo.simplepathfinder.nav.layered.ILayeredNavChunk;

@FunctionalInterface
public interface EdgeConsumer {
    void acceptEdge(int distance, int targetX, int targetY, int targetZ, AbstractLayeredNavChunk targetLayeredChunk, NavLinkType linkType);
}
