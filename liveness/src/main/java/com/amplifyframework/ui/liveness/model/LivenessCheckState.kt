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

package com.amplifyframework.ui.liveness.model

import android.graphics.RectF
import com.amplifyframework.ui.liveness.R
import com.amplifyframework.ui.liveness.ml.FaceDetector

internal sealed class LivenessCheckState(open val instructionId: Int? = null, open val isActionable: Boolean = true) {
    data class Initial(
        override val instructionId: Int? = null,
        override val isActionable: Boolean = true
    ) : LivenessCheckState(instructionId, isActionable) {
        companion object {
            fun withMoveFaceMessage() =
                Initial(R.string.amplify_ui_liveness_challenge_instruction_move_face)
            fun withMultipleFaceMessage() =
                Initial(R.string.amplify_ui_liveness_challenge_instruction_multiple_faces_detected)
            fun withMoveFaceFurtherAwayMessage() =
                Initial(R.string.amplify_ui_liveness_challenge_instruction_move_face_further)
            fun withConnectingMessage() =
                Initial(R.string.amplify_ui_liveness_challenge_connecting, false)
            fun withStartViewMessage() =
                Initial(R.string.amplify_ui_liveness_get_ready_center_face_label)
        }
    }
    data class Running(override val instructionId: Int? = null) : LivenessCheckState(instructionId, true) {
        companion object {
            fun withMoveFaceMessage() = Running(
                R.string.amplify_ui_liveness_challenge_instruction_move_face_closer
            )
            fun withMultipleFaceMessage() = Running(
                R.string.amplify_ui_liveness_challenge_instruction_multiple_faces_detected
            )
            fun withFaceOvalPosition(faceOvalPosition: FaceDetector.FaceOvalPosition) =
                Running(faceOvalPosition.instructionStringRes)
        }
    }
    object Error : LivenessCheckState(isActionable = false)
    class Success(val faceGuideRect: RectF) :
        LivenessCheckState(R.string.amplify_ui_liveness_challenge_verifying, false)
}
