package org.hime.draw

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JFrame

class Draw(title: String, width: Int, height: Int) {
    val frame: JFrame = JFrame(title)
    var image: BufferedImage
    var graphics: Graphics2D

    init {
        frame.setSize(width, height)
        frame.isResizable = false
        frame.isVisible = true
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

        image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        (image.graphics as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON)

        graphics = image.createGraphics()
        image = graphics.deviceConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT)
        graphics.dispose()
        graphics = image.createGraphics()
        graphics.color = Color.BLACK

        frame.add(DrawComponent(this, width, height))

    }

    fun update() {
        frame.repaint()
    }
}

