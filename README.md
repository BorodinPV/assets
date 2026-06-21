# Assets

Asset loading and management module. Handles 3D models, textures, and glTF scenes.

## Features

- **glTF 2.0** — full runtime loading via `jgltf-model`, including PBR materials, skins, animations, and texture registries
- **OBJ** — Wavefront OBJ loading via `de.javagl:obj`
- **Assimp** — additional model import through LWJGL Assimp bindings
- **Texture loading** — STB-based image loading
- **Mesh utilities** — simple and animated mesh abstractions, mesh rendering helpers

## Key Packages

| Package | Purpose |
|---|---|
| `ru.reweu.game.gltf` | glTF 2.0 parsing, PBR renderer, shader texture unit mapping, normal processing, tangent space, thin-glass materials |
| `ru.reweu.game.loader` | General model/texture/resource loading |
| `ru.reweu.game.render` | Shader-based render abstraction |

## Dependencies

- `engine` module
- LWJGL 3.3.4 (OpenGL, Assimp, STB)
- jgltf-model 2.0.4 (glTF 2.0 runtime)
- JOML 1.10.7 (math)
- de.javagl:obj 0.2.1 (OBJ loader)
