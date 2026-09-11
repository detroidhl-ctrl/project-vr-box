# Como gerar o APK sem Android Studio

## Opção 1 — Nuvem via GitHub Actions (recomendado, zero instalação pesada)

O workflow `.github/workflows/build-apk.yml` já está pronto no projeto. Ele builda o APK
num servidor do GitHub toda vez que você faz push — sua máquina só precisa de `git`.

Passos: envie o projeto pro GitHub, deixe o Actions rodar, baixe o APK gerado.

**Importante:** antes de fazer o primeiro push, baixe o modelo `hand_landmarker.task`
(link no README.md) e coloque em `app/src/main/assets/`. Sem ele o build compila
normalmente, mas o app crasha ao tentar detectar a mão em tempo de execução.

## Opção 2 — Local via linha de comando (sem IDE, só terminal)

Precisa de: JDK 17, Android SDK Command-line Tools (não é o Android Studio, é só a
ferramenta de linha de comando, ~150MB), e Gradle.

```bash
# 1. JDK 17 (exemplo Ubuntu/Debian; no Mac use `brew install openjdk@17`)
sudo apt install openjdk-17-jdk

# 2. Baixe "Command line tools only" em:
#    https://developer.android.com/studio#command-tools
# Extraia em ~/android-sdk/cmdline-tools/latest/

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 3. Instale os pacotes do SDK que o projeto usa
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 4. Instale o Gradle (ou baixe em gradle.org/releases)
sudo apt install gradle    # ou: sdk install gradle (via SDKMAN)

# 5. Dentro da pasta do projeto:
gradle assembleDebug

# O APK sai em:
# app/build/outputs/apk/debug/app-debug.apk
```

## Instalando o APK no celular

Com o arquivo `.apk` em mãos (baixado do Actions ou gerado localmente), duas formas:

- **Mais simples:** transfira o `.apk` pro celular (WhatsApp, Drive, cabo USB) e toque nele.
  Na primeira vez, o Android vai pedir pra habilitar "instalar apps de fontes desconhecidas"
  — autorize só para o app usado pra abrir o arquivo (ex: Arquivos/Chrome).
- **Via ADB** (se tiver o `platform-tools` instalado e o celular em modo depuração USB):
  ```bash
  adb install app-debug.apk
  ```
