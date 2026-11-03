/*
 * Adapted portions from Chat Resizer by shanktank.
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2026, shanktank
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.improvedchat.chatbox.resize;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class OverlayTransitions {
    private final ChatDialogBoxes dialogBoxes;
    private final TopLevelModals mainModals;

    @Inject
    OverlayTransitions(ChatDialogBoxes dialogBoxes, TopLevelModals mainModals) {
        this.dialogBoxes = dialogBoxes;
        this.mainModals = mainModals;
    }

    void reset() {
        dialogBoxes.reset();
        mainModals.reset();
    }

    Edges poll() {
        boolean dialogChanged = dialogBoxes.dialogOpenStateChanged();
        boolean modalChanged = mainModals.topLevelModalOpenStateChanged();
        return new Edges(dialogChanged, modalChanged);
    }

    boolean pollModalEdge() {
        return mainModals.topLevelModalOpenStateChanged();
    }

    static final class Edges {
        final boolean dialogChanged;
        final boolean modalChanged;

        Edges(boolean dialogChanged, boolean modalChanged) {
            this.dialogChanged = dialogChanged;
            this.modalChanged = modalChanged;
        }
    }
}