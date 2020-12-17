package com.nikitha.mymemory.models

import com.google.firebase.firestore.PropertyName

data class UserImageLists(
    @PropertyName("images")  val images: List<String>? = null
)
