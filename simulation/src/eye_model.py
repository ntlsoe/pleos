import json
from typing import List, Dict, Any
from geometry import Point3

class EyePair:
    __slots__ = ('left', 'right')
    def __init__(self, left: Point3, right: Point3):
        self.left = left
        self.right = right

    def __repr__(self) -> str:
        return f"EyePair(L={self.left}, R={self.right})"

class WeightedEyePair:
    __slots__ = ('eye_pair', 'weight', 'source', 'sample_id')
    def __init__(self, eye_pair: EyePair, weight: float, source: str, sample_id: str):
        self.eye_pair = eye_pair
        self.weight = float(weight)
        self.source = str(source)
        self.sample_id = str(sample_id)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "sampleId": self.sample_id,
            "weight": self.weight,
            "source": self.source,
            "leftEye": self.eye_pair.left.to_tuple(),
            "rightEye": self.eye_pair.right.to_tuple()
        }

class EyePopulationModel:
    def __init__(self, model_id: str, source: str, samples_config: List[Dict[str, Any]], nominal_eye: Point3):
        self.model_id = model_id
        self.source = source
        self.nominal_eye = nominal_eye
        self.samples_config = samples_config

    def generate_samples(self) -> List[WeightedEyePair]:
        result = []
        for s in self.samples_config:
            sid = s.get("id", "sample")
            weight = s.get("weight", 1.0)
            dx = s.get("deltaX", 0.0)
            dy = s.get("deltaY", 0.0)
            dz = s.get("deltaZ", 0.0)
            ipd = s.get("ipdMeters", 0.065)

            # Center of the two eyes
            center_x = self.nominal_eye.x + dx
            center_y = self.nominal_eye.y + dy
            center_z = self.nominal_eye.z + dz

            # LHD: Left eye is +Y (towards vehicle left/window), Right eye is -Y (towards vehicle center)
            half_ipd = ipd / 2.0
            left_eye = Point3(center_x, center_y + half_ipd, center_z)
            right_eye = Point3(center_x, center_y - half_ipd, center_z)

            pair = EyePair(left_eye, right_eye)
            result.append(WeightedEyePair(pair, weight, self.source, sid))
        return result

def load_eye_model(config_path: str, model_type: str, nominal_eye: Point3) -> EyePopulationModel:
    with open(config_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    if model_type == "SyntheticCenterProfile":
        sub = data["syntheticCenter"]
    elif model_type == "RepresentativePopulationApproximation":
        sub = data["representativePopulation"]
    else:
        raise ValueError(f"Unknown eye model type: {model_type}")

    return EyePopulationModel(
        model_id=sub["modelId"],
        source=sub["source"],
        samples_config=sub["samples"],
        nominal_eye=nominal_eye
    )
