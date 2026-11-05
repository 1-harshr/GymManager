package com.harsh.gymmanager

import com.harsh.gymmanager.domain.insights.NoOnDeviceModel
import com.harsh.gymmanager.domain.insights.OnDeviceModel

private var installed: OnDeviceModel = NoOnDeviceModel

/** Swift hands over Apple's on-device model at launch; until then, and on older iPhones, there's none. */
@Suppress("unused")
fun installOnDeviceModel(model: OnDeviceModel) {
    installed = model
}

/** Forwards to whatever Swift installed, so the app's dependencies can be built before it does. */
internal object InstalledOnDeviceModel : OnDeviceModel {
    override fun generate(instructions: String, prompt: String, onResult: (String?) -> Unit) =
        installed.generate(instructions, prompt, onResult)
}
