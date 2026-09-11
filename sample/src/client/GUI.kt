package xyz.atkdev.rbxkt

import com.rbxkt.types.classes.Players
import com.rbxkt.types.classes.ScreenGui
import com.rbxkt.types.classes.TextButton
import com.rbxkt.types.classes.UICorner
import com.rbxkt.types.classes.UIPadding
import com.rbxkt.types.datatypes.Color3
import com.rbxkt.types.datatypes.UDim
import com.rbxkt.types.datatypes.UDim2
import com.rbxkt.types.datatypes.Vector2
import com.rbxkt.types.enums.Font

fun createClicker() {
    val gui = ScreenGui {
        parent = Players.localPlayer.findFirstChild("PlayerGui")!!
    }

    val button = TextButton {
        text = "Click me for points!"
        textScaled = true
        anchorPoint = Vector2(0.5, 0.5)
        position = UDim2.fromScale(0.5, 0.8)
        backgroundColor3 = Color3(15, 15, 15)
        backgroundTransparency = 0.5
        font = Font.SourceSans
        parent = gui

        activated {
            println("Clicked!")
        }
    }

    UIPadding {
        paddingTop = UDim(0.1, 0)
        paddingBottom = UDim(0.1, 0)
        parent = button
    }

    UICorner {
        cornerRadius = UDim(0.1, 0)
        parent = button
    }
}
