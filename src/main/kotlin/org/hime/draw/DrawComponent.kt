package org.hime.draw

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

class DrawComponent(private val draw: Draw, width: Int, height: Int) : JComponent() {
    init {
        preferredSize = Dimension(width, height)
    }

    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g2.drawImage(draw.image, null, null)
    }
}