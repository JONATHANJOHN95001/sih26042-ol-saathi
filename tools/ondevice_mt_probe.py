"""Probe the on-device Hindi -> Santali model before it goes into the app.

Runs AI4Bharat IndicTrans2 indic-indic-dist-320M (int8 ONNX export by
hari31416) with nothing but onnxruntime and sentencepiece, the same minimal
pipeline the Android code uses, so what this prints is what the tablet will
produce. It translates the Hindi of every Santali pack line and prints the
model's output next to the pack's build-time translation, plus the time per
sentence.

    ./.venv-tts/Scripts/python.exe tools/ondevice_mt_probe.py [model_dir] [--limit N]
"""
import json
import sys
import time
import unicodedata
from pathlib import Path

import numpy as np
import onnxruntime as ort
import sentencepiece as spm

MODEL = Path(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") \
    else (Path.home() / "models" / "it2-indic-indic-320M-int8")
LIMIT = int(sys.argv[sys.argv.index("--limit") + 1]) if "--limit" in sys.argv else 12
PACK = Path(__file__).resolve().parents[1] / "app/src/main/assets/pack/pack.sat.json"

SRC_TAG, TGT_TAG = "hin_Deva", "sat_Olck"


class OnDeviceMT:
    def __init__(self, d: Path):
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 4
        self.enc = ort.InferenceSession(str(d / "encoder_model.onnx"), opts)
        self.dec = ort.InferenceSession(str(d / "decoder_model.onnx"), opts)
        self.dec_past = ort.InferenceSession(str(d / "decoder_with_past_model.onnx"), opts)
        self.layers = (len(self.dec.get_outputs()) - 1) // 4
        self.sp_src = spm.SentencePieceProcessor(model_file=str(d / "model.SRC"))
        self.src_vocab = json.loads((d / "dict.SRC.json").read_text(encoding="utf-8"))
        tgt = json.loads((d / "dict.TGT.json").read_text(encoding="utf-8"))
        self.tgt_inv = {v: k for k, v in tgt.items()}
        self.unk = self.src_vocab["<unk>"]
        self.eos = 2

    def encode(self, hindi: str) -> list[int]:
        text = unicodedata.normalize("NFC", hindi.strip())
        pieces = [SRC_TAG, TGT_TAG] + self.sp_src.encode_as_pieces(text)
        return [self.src_vocab.get(p, self.unk) for p in pieces] + [self.eos]

    def translate(self, hindi: str, max_new: int = 96) -> str:
        ids = np.array([self.encode(hindi)], dtype=np.int64)
        mask = np.ones_like(ids)
        enc = self.enc.run(["last_hidden_state"], {"input_ids": ids, "attention_mask": mask})[0]
        cur = np.array([[2]], dtype=np.int64)
        out, past = [], None
        for step in range(max_new):
            if step == 0:
                r = self.dec.run(None, {"input_ids": cur, "encoder_hidden_states": enc,
                                        "encoder_attention_mask": mask})
            else:
                feed = {"input_ids": cur, "encoder_attention_mask": mask}
                for i in range(self.layers):
                    b = i * 4
                    feed[f"past_key_values.{i}.decoder.key"] = past[b]
                    feed[f"past_key_values.{i}.decoder.value"] = past[b + 1]
                    feed[f"past_key_values.{i}.encoder.key"] = past[b + 2]
                    feed[f"past_key_values.{i}.encoder.value"] = past[b + 3]
                r = self.dec_past.run(None, feed)
            logits, past = r[0], r[1:]
            nxt = int(np.argmax(logits[0, -1, :]))
            if nxt == self.eos:
                break
            out.append(nxt)
            cur = np.array([[nxt]], dtype=np.int64)
        pieces = [self.tgt_inv.get(i, "") for i in out]
        pieces = [p for p in pieces if not (p.startswith("<") and p.endswith(">"))]
        return "".join(pieces).replace("\u2581", " ").strip()


def main():
    t0 = time.time()
    mt = OnDeviceMT(MODEL)
    print(f"loaded in {time.time() - t0:.1f}s, {mt.layers} decoder layers")
    raw = json.loads(PACK.read_text(encoding="utf-8"))["entries"]
    entries = [dict(v, id=k) for k, v in raw.items()]
    same = 0
    for e in entries[:LIMIT]:
        t = time.time()
        got = mt.translate(e["source"])
        ms = (time.time() - t) * 1000
        match = got.replace(" ", "") == e["target"].replace(" ", "")
        same += match
        print(f"\n[{e['id']}] {e['source']}\n  pack : {e['target']}\n  model: {got}   ({ms:.0f} ms){'  SAME' if match else ''}")
    for extra in ["आज बारिश हो रही है।", "तुम्हारा नाम क्या है?", "मुझे पानी चाहिए।"]:
        t = time.time()
        print(f"\n[new] {extra}\n  model: {mt.translate(extra)}   ({(time.time() - t) * 1000:.0f} ms)")
    print(f"\nidentical to pack: {same}/{min(LIMIT, len(entries))}")


if __name__ == "__main__":
    main()
