/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.firebase.geofire.geohash

/* number of bits per base 32 character */
internal const val BITS_PER_BASE32_CHAR = 5
internal const val BASE32_CHARS = "0123456789bcdefghjkmnpqrstuvwxyz"

internal fun Int.toBase32Char(): Char =
    BASE32_CHARS.getOrNull(this)
        ?: error("Not a valid base32 value: $this")

internal fun Char.toBase32Value(): Int =
    BASE32_CHARS.indexOf(this).takeIf { it != -1 }
        ?: error("Not a valid base32 char: $this")