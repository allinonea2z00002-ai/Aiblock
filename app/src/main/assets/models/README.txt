PURESHIELD AI — Offline ONNX model
==================================

The app downloads the model once on the user's explicit action. After the
verified model is stored in app-private files, image/video inference is local
and does not need an internet connection.

Model repository:
https://huggingface.co/kasimtmc/nsfw-image-detection-384-onnx

File: nsfw_vit_tiny_384_int8.onnx
Runtime filename: nsfw-image-detection-384.onnx
Format: ONNX Runtime, dynamic-int8 quantized
Input: pixel_values, float32 [1, 3, 384, 384], RGB, NCHW
Normalization: (pixel / 255 - 0.5) / 0.5
Output: logits [1, 2], order [NSFW, SFW], softmax
SHA-256: 20cc99a8986320972273253325975203f2a647ca0010a1ac9c869a0dbb9eb77d

The app verifies SHA-256 before installing the downloaded model. It is
released under Apache-2.0; see the model repository for attribution and license.

IMPORTANT LIMITATIONS
- This is a binary NSFW/Safe classifier, not four separate Safe/Suggestive/
  Nudity/Explicit labels.
- It can produce false positives (for example, swimwear or art) and false
  negatives. The published evaluation is not a guarantee for this app's data.
- Video scan samples frames; it does not inspect every frame or audio track.
- Background library scanning is periodic and limited to recent media. It is
  subject to Android media permissions, battery restrictions and OEM behavior.
- Flagged media is copied to app-private quarantine. The original is retained.
