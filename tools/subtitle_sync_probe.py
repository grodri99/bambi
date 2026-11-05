#!/usr/bin/env python3
"""Offline probe for Bambi's subtitle timing heuristics.

No video, API key, or third-party package is needed. A known-good embedded
subtitle is the usual reference; external SRT/VTT/ASS files are the candidates.
Mono PCM audio samples can instead provide a rough speech-activity reference.

Examples:
    python3 tools/subtitle_sync_probe.py --self-test
    python3 tools/subtitle_sync_probe.py --reference reference.srt candidates/*.srt
    python3 tools/subtitle_sync_probe.py --reference reference.srt --window 180 candidate.srt
    python3 tools/subtitle_sync_probe.py --audio-sample 900=900.wav --audio-sample 3000=3000.wav candidates/*.srt

GOOD uses the app's strong-result thresholds. REVIEW means that the current app
would need repeated confirmations; it is not enough to auto-select a candidate.
"""

from __future__ import annotations

import argparse
import math
import random
import re
import sys
import wave
from array import array
from dataclasses import dataclass
from pathlib import Path

BIN_MS = 100
MAX_OFFSET_MS = 300_000
MIN_REFERENCE_CUES = 10
MIN_WINDOW_MS = 20_000
PEAK_EXCLUSION_BINS = 20
SCALES = (1.0, 25.0 / 23.976, 23.976 / 25.0)


@dataclass(frozen=True)
class Interval:
    start_ms: int
    end_ms: int


@dataclass(frozen=True)
class SyncResult:
    offset_ms: int
    scale: float
    correlation: float
    ratio: float
    reference_cues: int

    @property
    def strong(self) -> bool:
        if self.scale == 1.0:
            return self.correlation >= 0.55 and self.ratio >= 1.5
        return self.correlation >= 0.60 and self.ratio >= 1.8

    @property
    def plausible(self) -> bool:
        return self.scale == 1.0 and self.correlation >= 0.33 and self.ratio >= 1.15

    @property
    def consistent(self) -> bool:
        return self.scale == 1.0 and self.correlation >= 0.35 and self.ratio >= 1.03

    @property
    def verdict(self) -> str:
        if self.strong:
            return "GOOD"
        if self.plausible or self.consistent:
            return "REVIEW"
        return "REJECT"


@dataclass
class Candidate:
    offset_ms: int
    scale: float
    score: float
    second: float
    adjusted: float = 0.0


def parse_timestamp(value: str) -> int:
    parts = value.strip().replace(",", ".").split(":")
    seconds = float(parts[-1])
    minutes = int(parts[-2])
    hours = int(parts[-3]) if len(parts) >= 3 else 0
    return round((hours * 3600 + minutes * 60 + seconds) * 1000)


def parse_subtitle(path: Path) -> list[Interval]:
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = raw.decode("cp1252")

    intervals: list[Interval] = []
    for line in text.splitlines():
        if line.lstrip().lower().startswith("dialogue:"):
            fields = line.split(",", 9)
            if len(fields) >= 3:
                try:
                    intervals.append(Interval(parse_timestamp(fields[1]), parse_timestamp(fields[2])))
                except (ValueError, IndexError):
                    pass
            continue
        if "-->" not in line:
            continue
        left, right = line.split("-->", 1)
        right = right.strip().split()[0]
        try:
            intervals.append(Interval(parse_timestamp(left), parse_timestamp(right)))
        except (ValueError, IndexError):
            pass

    return merge_intervals(i for i in intervals if i.end_ms > i.start_ms)


def merge_intervals(intervals) -> list[Interval]:
    out: list[Interval] = []
    for interval in sorted(intervals, key=lambda i: (i.start_ms, i.end_ms)):
        if out and interval.start_ms <= out[-1].end_ms + 1:
            out[-1] = Interval(out[-1].start_ms, max(out[-1].end_ms, interval.end_ms))
        else:
            out.append(interval)
    return out


def sample_windows(reference: list[Interval], window_seconds: int) -> list[Interval]:
    """Take cue-anchored beginning/middle/end samples, including sparse remote extracts."""
    first = reference[0].start_ms
    last = max(i.end_ms for i in reference)
    window_ms = max(window_seconds, MIN_WINDOW_MS // 1000) * 1000
    if last - first <= window_ms:
        return [Interval(first, last + 1000)]

    anchors = (reference[len(reference) // 6], reference[len(reference) // 2], reference[len(reference) * 5 // 6])
    windows: list[Interval] = []
    for anchor in anchors:
        start = max(first, anchor.start_ms - window_ms // 4)
        end = min(last + 1000, start + window_ms)
        if end - start < MIN_WINDOW_MS:
            start = max(first, end - window_ms)
        windows.append(Interval(start, end))
    return windows


def reference_signal(refs: list[Interval], window: Interval) -> list[int]:
    size = (window.end_ms - window.start_ms) // BIN_MS
    signal = [0] * size
    for ref in refs:
        start = (max(ref.start_ms, window.start_ms) - window.start_ms) // BIN_MS
        end = (min(ref.end_ms, window.end_ms) - window.start_ms) // BIN_MS
        for index in range(max(0, start), min(size, end)):
            signal[index] = 1
    return signal


def best_offset(
    signals: list[tuple[int, list[int]]],
    windows: list[Interval],
    external: list[Interval],
    scale: float,
) -> Candidate | None:
    # Intervals farther than MAX_OFFSET from every sampled window cannot affect the score.
    video_start = min(w.start_ms for w in windows)
    video_end = max(w.end_ms for w in windows)
    cropped = [
        i for i in external
        if i.end_ms * scale >= video_start - MAX_OFFSET_MS
        and i.start_ms * scale <= video_end + MAX_OFFSET_MS
    ]
    if not cropped:
        return None

    low = math.floor(min(i.start_ms for i in cropped) * scale / BIN_MS) - 1
    high = math.ceil(max(i.end_ms for i in cropped) * scale / BIN_MS) + 1
    size = high - low + 1
    if size <= 0:
        return None
    ext = [False] * size
    for interval in cropped:
        start = int(math.floor(interval.start_ms * scale / BIN_MS) - low)
        end = int(math.floor(interval.end_ms * scale / BIN_MS) - low)
        for index in range(max(0, start), min(size, end)):
            ext[index] = True

    count = sum(len(signal) for _, signal in signals)
    sum_reference = sum(sum(signal) for _, signal in signals)
    variance_reference = sum_reference - sum_reference * sum_reference / count
    if variance_reference <= 0:
        return None

    max_bins = MAX_OFFSET_MS // BIN_MS
    scores = [0.0] * (2 * max_bins + 1)
    for index in range(len(scores)):
        offset_bins = index - max_bins
        sum_external = 0
        sum_both = 0
        for start_ms, signal in signals:
            base = start_ms // BIN_MS - offset_bins - low
            start = max(0, -base)
            end = min(len(signal), size - base)
            for signal_index in range(start, end):
                if ext[base + signal_index]:
                    sum_external += 1
                    sum_both += signal[signal_index]
        variance_external = sum_external - sum_external * sum_external / count
        scores[index] = (
            (sum_both - sum_reference * sum_external / count)
            / math.sqrt(variance_reference * variance_external)
            if variance_external > 0 else -1.0
        )

    best_index = max(range(len(scores)), key=scores.__getitem__)
    second = max(
        (score for index, score in enumerate(scores) if abs(index - best_index) > PEAK_EXCLUSION_BINS),
        default=-1.0,
    )
    return Candidate(
        offset_ms=(best_index - max_bins) * BIN_MS,
        scale=scale,
        score=scores[best_index],
        second=second,
    )


def refine(
    refs: list[Interval],
    windows: list[Interval],
    external: list[Interval],
    scale: float,
    coarse: int,
) -> int:
    video_start = min(w.start_ms for w in windows)
    video_end = max(w.end_ms for w in windows)
    cropped = [
        i for i in external
        if i.end_ms * scale + coarse + BIN_MS >= video_start
        and i.start_ms * scale + coarse - BIN_MS <= video_end
    ]
    best_offset = coarse
    best_score = -(2**63)
    for offset in range(coarse - BIN_MS, coarse + BIN_MS + 1, 10):
        score = 0
        for interval in cropped:
            start = int(interval.start_ms * scale) + offset
            end = int(interval.end_ms * scale) + offset
            for window in windows:
                left = max(start, window.start_ms)
                right = min(end, window.end_ms)
                if right <= left:
                    continue
                overlap = sum(
                    max(0, min(right, ref.end_ms) - max(left, ref.start_ms))
                    for ref in refs
                )
                score += 2 * overlap - (right - left)
        if score > best_score:
            best_score = score
            best_offset = offset
    return best_offset


def align(reference: list[Interval], windows: list[Interval], external: list[Interval]) -> SyncResult | None:
    usable = [w for w in windows if w.end_ms - w.start_ms >= MIN_WINDOW_MS]
    if not usable or not external:
        return None
    refs = [
        ref for ref in reference
        if any(w.start_ms <= ref.start_ms and ref.end_ms <= w.end_ms for w in usable)
    ]
    if len(refs) < MIN_REFERENCE_CUES:
        return None
    signals = [(w.start_ms, reference_signal(refs, w)) for w in usable]

    best: Candidate | None = None
    for scale in SCALES:
        candidate = best_offset(signals, usable, external, scale)
        if candidate is None:
            continue
        candidate.adjusted = candidate.score if scale == 1.0 else candidate.score * 0.97
        if best is None or candidate.adjusted > best.adjusted:
            best = candidate
    if best is None:
        return None

    ratio = 99.0 if best.second <= 0 else best.score / best.second
    fine = refine(refs, usable, external, best.scale, best.offset_ms)
    return SyncResult(fine, best.scale, best.score, ratio, len(refs))


def evaluate(
    reference: list[Interval],
    candidate: list[Interval],
    window_seconds: int,
    windows: list[Interval] | None = None,
) -> SyncResult | None:
    return align(reference, windows or sample_windows(reference, window_seconds), candidate)


def audio_activity(path: Path, start_ms: int, threshold_delta_db: float) -> tuple[list[Interval], Interval, float, float, float]:
    """Estimate speech-active bins from RMS energy above the sample's noise floor."""
    with wave.open(str(path), "rb") as wav:
        if wav.getnchannels() != 1 or wav.getsampwidth() != 2:
            raise ValueError("audio samples must be mono 16-bit PCM WAV")
        rate = wav.getframerate()
        frames = wav.getnframes()
        raw = wav.readframes(frames)
    duration_ms = round(frames * 1000 / rate)
    bin_frames = max(1, rate * BIN_MS // 1000)
    levels: list[float] = []
    for offset in range(0, len(raw), bin_frames * 2):
        samples = array("h")
        samples.frombytes(raw[offset:offset + bin_frames * 2])
        if sys.byteorder != "little":
            samples.byteswap()
        if not samples:
            continue
        rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples))
        levels.append(20 * math.log10(max(rms, 1) / 32768))
    if not levels:
        raise ValueError("audio sample is empty")
    ordered = sorted(levels)
    noise_db = ordered[len(ordered) * 20 // 100]
    threshold_db = min(-20.0, max(-45.0, noise_db + threshold_delta_db))
    active = [level >= threshold_db for level in levels]

    # Bridge very short pauses and drop isolated noise spikes.
    for index in range(len(active)):
        if active[index]:
            continue
        left = index - 1
        while left >= 0 and not active[left]:
            left -= 1
        right = index
        while right < len(active) and not active[right]:
            right += 1
        if left >= 0 and right < len(active) and right - left - 1 <= 3:
            for fill in range(left + 1, right):
                active[fill] = True
    intervals: list[Interval] = []
    index = 0
    while index < len(active):
        if not active[index]:
            index += 1
            continue
        end = index + 1
        while end < len(active) and active[end]:
            end += 1
        if end - index >= 2:
            intervals.append(Interval(start_ms + index * BIN_MS, start_ms + end * BIN_MS))
        index = end
    window = Interval(start_ms, start_ms + duration_ms)
    activity_ratio = sum(active) / len(active)
    return intervals, window, noise_db, threshold_db, activity_ratio


def transform(reference: list[Interval], scale: float = 1.0, offset_ms: int = 0) -> list[Interval]:
    """Create a subtitle where video_time = subtitle_time * scale + offset."""
    return [
        Interval(round((i.start_ms - offset_ms) / scale), round((i.end_ms - offset_ms) / scale))
        for i in reference
        if (i.end_ms - offset_ms) / scale > 0
    ]


def synthetic_reference() -> list[Interval]:
    rng = random.Random(19)
    out: list[Interval] = []
    position = 5_000
    while position < 15 * 60_000:
        duration = rng.randrange(700, 2600, 100)
        out.append(Interval(position, position + duration))
        position += duration + rng.randrange(900, 4800, 100)
    return out


def self_test(window_seconds: int) -> int:
    reference = synthetic_reference()
    wrong = synthetic_reference()
    rng = random.Random(81)
    wrong = [
        Interval(i.start_ms + rng.randrange(-2000, 2001, 100), i.end_ms + rng.randrange(1800, 4200, 100))
        for i in wrong
    ]
    wrong = merge_intervals(i for i in wrong if i.end_ms > i.start_ms)
    cases = [
        ("correct +10.4s", transform(reference, offset_ms=10_400), True, 10_400, 1.0),
        ("correct FPS conversion", transform(reference, scale=25.0 / 23.976), True, 0, 25.0 / 23.976),
        ("wrong content", wrong, False, None, None),
    ]
    failures = 0
    print("Synthetic self-test (three sampled scenes per candidate)")
    for name, candidate, should_pass, expected_offset, expected_scale in cases:
        result = evaluate(reference, candidate, window_seconds)
        passed = result is not None and result.strong == should_pass
        if passed and should_pass and result is not None:
            passed = (
                abs(result.offset_ms - expected_offset) <= 100
                and abs(result.scale - expected_scale) < 0.0001
            )
        failures += 0 if passed else 1
        print(f"  {'PASS' if passed else 'FAIL'}  {name}: {format_result(result)}")
    print("Self-test:", "PASS" if failures == 0 else f"FAIL ({failures} case(s))")
    return 1 if failures else 0


def format_result(result: SyncResult | None) -> str:
    if result is None:
        return "insufficient reference data"
    return (
        f"{result.verdict} offset={result.offset_ms / 1000:+.2f}s scale={result.scale:.6f} "
        f"correlation={result.correlation:.3f} ratio={result.ratio:.2f} refs={result.reference_cues}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("candidates", nargs="*", type=Path, help="SRT, VTT, or ASS subtitle files")
    parser.add_argument("--reference", type=Path, help="known-good embedded subtitle used as timing reference")
    parser.add_argument(
        "--audio-sample",
        action="append",
        default=[],
        metavar="START_SECONDS=WAV",
        help="mono 16-bit PCM audio sample; repeat for multiple scenes (no subtitle reference needed)",
    )
    parser.add_argument("--audio-threshold-db", type=float, default=8.0, help="decibels above noise floor (default: 8)")
    parser.add_argument("--window", type=int, default=120, help="seconds per beginning/middle/end sample (default: 120)")
    parser.add_argument("--self-test", action="store_true", help="run deterministic synthetic cases")
    args = parser.parse_args()
    if args.window * 1000 < MIN_WINDOW_MS:
        parser.error("--window must be at least 20 seconds")
    if args.self_test:
        return self_test(args.window)
    if (args.reference is None) == (not args.audio_sample) or not args.candidates:
        parser.error("provide one reference type (--reference or --audio-sample) and at least one candidate, or use --self-test")

    explicit_windows: list[Interval] | None = None
    if args.audio_sample:
        reference = []
        explicit_windows = []
        for specification in args.audio_sample:
            try:
                start_text, path_text = specification.split("=", 1)
                start_ms = round(float(start_text) * 1000)
                intervals, window, noise_db, threshold_db, activity_ratio = audio_activity(
                    Path(path_text), start_ms, args.audio_threshold_db
                )
            except (ValueError, OSError) as error:
                parser.error(f"invalid audio sample {specification!r}: {error}")
            reference.extend(intervals)
            explicit_windows.append(window)
            print(
                f"Audio {path_text}: cues={len(intervals)} noise={noise_db:.1f}dB "
                f"threshold={threshold_db:.1f}dB active={activity_ratio:.0%}"
            )
        reference.sort(key=lambda interval: interval.start_ms)
    else:
        reference = parse_subtitle(args.reference)
    if len(reference) < MIN_REFERENCE_CUES:
        parser.error(f"reference has only {len(reference)} cues; at least {MIN_REFERENCE_CUES} are required")

    results: list[tuple[Path, SyncResult | None]] = []
    for path in args.candidates:
        try:
            result = evaluate(reference, parse_subtitle(path), args.window, explicit_windows)
        except (OSError, UnicodeError) as error:
            print(f"{path}: ERROR {error}", file=sys.stderr)
            result = None
        results.append((path, result))

    results.sort(
        key=lambda item: (
            item[1].strong if item[1] is not None else False,
            item[1].correlation if item[1] is not None else -1.0,
            item[1].ratio if item[1] is not None else -1.0,
        ),
        reverse=True,
    )
    for path, result in results:
        print(f"{path}: {format_result(result)}")

    if not any(result is not None and result.strong for _, result in results):
        print("No candidate reached the automatic-selection threshold.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
