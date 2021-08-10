package com.sample.protifydroid

import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MessengerScopePool {
    private val scopes = ConcurrentHashMap<UUID, MessengerScope>()
    fun registerScope(scope: MessengerScope) {
        scopes[scope.uuid] = scope
    }
    fun destroyScope(uuid: UUID) {
        scopes[uuid]?.destroy()
    }
}