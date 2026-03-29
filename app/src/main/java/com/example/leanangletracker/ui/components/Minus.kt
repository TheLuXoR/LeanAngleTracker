package com.example.leanangletracker.ui.components


import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
@Preview(heightDp = 100, widthDp = 100)
fun Test(){
    Icon(Minus, contentDescription = "foo", modifier = Modifier.size(18.dp))

}

public val Minus: ImageVector
    get() {
        if (_minus != null) {
            return _minus!!
        }
        _minus = materialIcon(name = "Filled.Add") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(-8.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _minus!!
    }

private var _minus: ImageVector? = null
