import json
import sentencepiece as spm

ASSETS = r"app\src\main\assets\file_tra"

# Load IndicTrans2 SentencePiece model
sp = spm.SentencePieceProcessor()
sp.Load(ASSETS + r"\model.SRC")

# Load IndicTrans2's actual model vocabulary
with open(ASSETS + r"\dict.SRC.json", "r", encoding="utf-8") as f:
    vocab = json.load(f)

mapping = []
missing = []

for sp_id in range(sp.GetPieceSize()):
    piece = sp.IdToPiece(sp_id)

    if piece in vocab:
        model_id = vocab[piece]
    else:
        model_id = vocab.get("<unk>", 3)
        missing.append((sp_id, piece))

    mapping.append(model_id)

# Save native SentencePiece ID -> IndicTrans2 model ID
output = ASSETS + r"\spm_to_model_src.json"

with open(output, "w", encoding="utf-8") as f:
    json.dump(mapping, f)

print("SentencePiece vocabulary size:", sp.GetPieceSize())
print("IndicTrans2 vocabulary size:", len(vocab))
print("Missing pieces:", len(missing))
print("Saved:", output)

if missing:
    print("\nFirst 20 missing pieces:")
    for item in missing[:20]:
        print(item)