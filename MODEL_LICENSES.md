# FaceSwap9 neural model bundle — license notes

FaceSwap9 keeps neural weights outside the APK. The user downloads each model
directly from its upstream release source into the app's private storage.

| Model | Upstream | Declared license / terms |
|---|---|---|
| ghost_1_256.onnx | AI-Forever via FaceFusion assets | Apache-2.0 |
| crossface_ghost.onnx | FaceFusion assets | OpenRAIL-M |
| arcface_w600k_r50.onnx | InsightFace via FaceFusion assets | Non-Commercial |

The model catalog and version references are in `NeuralModelStore.kt`.
The upstream FaceFusion model license page lists each asset separately:
https://docs.facefusion.io/introduction/licenses

Important:
- The application source code and model weights are separate works with separate terms.
- This private, non-commercial build does not redistribute the model files.
- The ArcFace and CrossFace terms still apply to private use; do not assume that
  private use removes every condition in those licenses.
- Before any public release or commercial use, replace restricted weights with
  appropriately licensed alternatives and review the upstream terms again.
- Use face images only with the permission of the people depicted.
