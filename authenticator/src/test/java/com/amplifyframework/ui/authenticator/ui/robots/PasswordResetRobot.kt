/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.ui.authenticator.ui.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.amplifyframework.ui.authenticator.forms.FieldKey
import com.amplifyframework.ui.authenticator.ui.TestTags
import com.amplifyframework.ui.testing.ComposeTest

fun ComposeTest.passwordReset(func: PasswordResetRobot.() -> Unit) = PasswordResetRobot(composeTestRule).func()

class PasswordResetRobot(rule: ComposeTestRule) : ScreenLevelRobot(rule) {
    fun hasSubmitButton(expected: String) = assertExists(TestTags.PasswordResetButton, expected)

    fun setUsername(value: String) = setFieldContent(FieldKey.Username, value)
}
