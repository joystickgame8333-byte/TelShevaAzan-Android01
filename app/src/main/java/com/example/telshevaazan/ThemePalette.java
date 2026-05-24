package com.example.telshevaazan;

final class ThemePalette {
    final String id;
    final String title;
    final String modeTitle;
    final int backgroundTop;
    final int backgroundMiddle;
    final int backgroundBottom;
    final int accent;
    final int primaryText;
    final int secondaryText;
    final int mutedText;
    final int panel;
    final int row;
    final int activeRow;
    final int border;
    final int activeBorder;
    final int control;
    final int controlPressed;
    final int countdown;
    final boolean night;

    ThemePalette(
            String id,
            String title,
            String modeTitle,
            int backgroundTop,
            int backgroundMiddle,
            int backgroundBottom,
            int accent,
            int primaryText,
            int secondaryText,
            int mutedText,
            int panel,
            int row,
            int activeRow,
            int border,
            int activeBorder,
            int control,
            int controlPressed,
            int countdown,
            boolean night
    ) {
        this.id = id;
        this.title = title;
        this.modeTitle = modeTitle;
        this.backgroundTop = backgroundTop;
        this.backgroundMiddle = backgroundMiddle;
        this.backgroundBottom = backgroundBottom;
        this.accent = accent;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.mutedText = mutedText;
        this.panel = panel;
        this.row = row;
        this.activeRow = activeRow;
        this.border = border;
        this.activeBorder = activeBorder;
        this.control = control;
        this.controlPressed = controlPressed;
        this.countdown = countdown;
        this.night = night;
    }
}
