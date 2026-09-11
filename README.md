# VR/AR Engine — Base Nativa (Android/Kotlin)

Esqueleto funcional dos 3 sistemas centrais pedidos:

1. **Passthrough de câmera** (`camera/`, `render/`) — Camera2 API + OpenGL ES (textura OES externa), sem View/WebView no caminho crítico.
2. **Detecção de pinça** (`gesture/`) — MediaPipe Tasks Vision (Hand Landmarker), 100% on-device, GPU delegate.
3. **Painel fixo no mundo** (`panel/`, `orientation/`) — orientação por `TYPE_ROTATION_VECTOR` (giroscópio fundido com acelerômetro/magnetômetro) + matemática de quaternion para projetar a posição do painel na tela a cada frame.

## Por que essa stack e não Unity/WebXR

- Você pediu "sistema nativo/robusto", "zero lag" e "empacotar em APK depois" → isso é literalmente o ciclo de um app Android nativo (não precisa de engine intermediária).
- MediaPipe Hands roda nativamente no Android com delegate de GPU — não existe hand tracking pronto no ARCore/AR Foundation, então usar Unity só adicionaria uma ponte extra (plugin nativo → C# → Unity) sem ganho nenhum.
- Passthrough via Camera2 + textura OES é o mesmo caminho que o próprio ARCore usa por baixo dos panos — é o mais direto possível entre sensor e tela.

## Setup

1. Abra a pasta `vrar-engine/` no Android Studio (Hedgehog+).
2. Baixe o modelo `hand_landmarker.task` (Google, licença Apache 2.0) em:
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
   e coloque em `app/src/main/assets/hand_landmarker.task`.
3. `minSdk 26` (delegate de GPU do MediaPipe prefere API 26+).
4. Rode direto num aparelho físico (emulador não tem câmera/giroscópio reais confiáveis) — use "Run" normal do Android Studio por enquanto; isso já gera um APK de debug instalado via ADB, sem precisar de pipeline de release.

## Como a matemática do painel funciona (`panel/WorldLockedPanel.kt`)

- O painel guarda uma **direção no mundo** (vetor unitário), não uma posição de tela.
- A cada frame: pega o quaternion atual do giroscópio → gira a direção do painel para o espaço da câmera (rotação inversa) → projeta com uma câmera pinhole simples (FOV configurável) → vira coordenada de tela.
- Se a projeção cai atrás do usuário ou muito fora do FOV, o painel some (mesmo comportamento do Quest 3 quando você olha para outro lugar).
- **Arrastar com a pinça** só atualiza a direção-no-mundo enquanto a pinça está ativa E começou dentro dos limites do painel. Ao soltar, a nova direção fica congelada — é isso que dá o efeito "world-locked".

## Como a pinça é detectada (`gesture/HandGestureDetector.kt`)

- Distância entre `thumb_tip` (landmark 4) e `index_finger_tip` (landmark 8), **normalizada** pela distância `wrist–middle_finger_mcp` (landmarks 0–9). Isso torna o threshold independente da distância da mão até a câmera.
- Histerese (threshold de entrada ≠ threshold de saída) para evitar "flicker" quando o dedo fica bem na borda do gesto.

## Limitações conhecidas / próximos passos (importante)

- **Rotação/aspect ratio da imagem da câmera**: a imagem crua do sensor Camera2 costuma vir em orientação "paisagem nativa do sensor" e pode não bater 1:1 com a tela em landscape do app nem com o aspect ratio usado pelo MediaPipe. Antes de calibrar o threshold de pinça em produção, valide a rotação (`CameraCharacteristics.SENSOR_ORIENTATION`) no seu aparelho específico.
- **`YuvConverter`** usa `YuvImage` + JPEG como caminho rápido de desenvolvimento (tem overhead de compressão). Para latência mínima de verdade, trocar por uma conversão YUV→RGB via RenderScript/OpenGL direta (ver `YuvToRgbConverter` do repositório oficial `android/camera-samples`).
- **`horizontalFovDegrees`** no `WorldLockedPanel` precisa ser calibrado por lente de VR Box (varia por fabricante, tipicamente 80–100°).
- Visão estéreo (split screen) foi propositalmente deixada de fora, como combinado — a estrutura de renderização já separa "textura da câmera" de "overlay do painel", então adicionar um segundo viewport lado a lado depois é direto.
- Nenhuma persistência de "múltiplos painéis" ainda — só o painel único do protótipo.

## Estrutura

```
app/src/main/java/com/vrarengine/core/
├── MainActivity.kt              orquestra tudo, loop de frame via Choreographer
├── camera/
│   ├── CameraPassthroughManager.kt   Camera2: 1 surface p/ preview em alta res, 1 ImageReader p/ MediaPipe
│   └── YuvConverter.kt               conversão YUV_420_888 -> Bitmap
├── render/
│   └── PassthroughRenderer.kt        shader OES fullscreen (o passthrough em si)
├── orientation/
│   └── OrientationTracker.kt         sensor de rotação -> quaternion
├── panel/
│   ├── Quaternion.kt                 rotação de vetor por quaternion
│   └── WorldLockedPanel.kt           projeção do painel + lógica de arrasto
└── gesture/
    ├── PinchState.kt
    └── HandGestureDetector.kt        MediaPipe HandLandmarker -> matemática da pinça
```
