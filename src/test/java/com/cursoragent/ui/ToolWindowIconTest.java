package com.cursoragent.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.CustomIconUtilKt;
import com.intellij.ui.icons.ScaledIconCacheKt;
import com.intellij.ui.icons.StrokeKt;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ToolWindowIconTest {
    @Test
    void selectedIconsUseNativeForegroundAndKeepTheirOutline() {
        boolean wasActive = ScaledIconCacheKt.isIconActivated;
        IconLoader.activate();
        try {
            var base = (CachedImageIcon) IconLoader.getIcon("/icons/cursor-agent.svg", getClass());
            for (int size : new int[]{16, 20}) {
                for (boolean dark : new boolean[]{false, true}) {
                    Icon icon = CustomIconUtilKt.loadIconCustomVersionOrScale(base, size, dark, null);
                    int[] normal = pixels(icon, size);
                    assertColor(normal, dark ? 0xCED0D6 : 0x6C707E);
                    // SquareStripeButtonLook uses this SDK conversion with the theme's selected foreground.
                    int[] selected = pixels(StrokeKt.toStrokeIcon(icon, Color.WHITE), size);
                    assertColor(selected, 0xFFFFFF);
                    for (int i = 0; i < normal.length; i++) {
                        assertEquals(normal[i] >>> 24, selected[i] >>> 24, "Outline/opacity changed");
                    }
                    assertArrayEquals(normal, pixels(icon, size), "Deselect must retain the original icon");
                }
            }
        } finally {
            if (!wasActive) IconLoader.deactivate();
        }
    }

    private static int[] pixels(Icon icon, int size) {
        assertEquals(size, icon.getIconWidth());
        assertEquals(size, icon.getIconHeight());
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image.getRGB(0, 0, size, size, null, 0, size);
    }

    private static void assertColor(int[] pixels, int expected) {
        // Compare the dominant color; antialiased edge pixels have rounding noise.
        var colors = java.util.Arrays.stream(pixels)
                .filter(pixel -> (pixel >>> 24) >= 128)
                .map(pixel -> pixel & 0xFFFFFF).boxed()
                .collect(java.util.stream.Collectors.groupingBy(pixel -> pixel, java.util.stream.Collectors.counting()));
        assertFalse(colors.isEmpty(), "Icon must contain visible strokes");
        int dominant = java.util.Collections.max(colors.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
        assertEquals(expected, dominant);
    }
}
