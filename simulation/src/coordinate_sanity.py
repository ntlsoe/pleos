import os
import json
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from geometry import Point3
from pillar_model import load_vehicle_pillars

def run_coordinate_sanity_check():
    config_dir = os.path.join(os.path.dirname(__file__), "..", "config")
    profiles = [
        "representative_sedan_v0.json",
        "symmetric_test_profile.json",
        "representative_suv_v0.json",
        "representative_mpv_v0.json"
    ]

    results = []
    print("=== ISO 8855 Coordinate Sanity Check ===")

    for p_name in profiles:
        p_path = os.path.join(config_dir, p_name)
        geom = load_vehicle_pillars(p_path)

        wb = geom.wheelbase
        eye = geom.driver_eye_nominal
        lp = geom.left_pillar.vertices
        rp = geom.right_pillar.vertices

        # Condition 1: Front axle X = wheelbase > 0
        cond1 = (wb > 0)
        # Condition 2: Rear axle X = 0 (origin definition)
        cond2 = True
        # Condition 3: Driver eye inside cabin: 0 < X_eye < wb and |Y_eye| < track_width/2
        cond3 = (0.0 < eye.x < wb) and (abs(eye.y) < geom.track_width / 2.0) and (eye.z > 0.8)
        # Condition 4: A-pillar physically ahead of driver eye: X_base > X_eye and X_top > X_eye
        lp_base_x = min(v.x for v in lp[:4])
        lp_top_x = min(v.x for v in lp[4:])
        rp_base_x = min(v.x for v in rp[:4])
        rp_top_x = min(v.x for v in rp[4:])
        cond4 = (lp_base_x > eye.x) and (lp_top_x > eye.x) and (rp_base_x > eye.x) and (rp_top_x > eye.x)
        # Condition 5: Windshield rakes rearward: Base X > Top X
        cond5 = (lp_base_x > lp_top_x) and (rp_base_x > rp_top_x)

        all_passed = cond1 and cond2 and cond3 and cond4 and cond5
        results.append({
            "profile": geom.profile_id,
            "wheelbase": wb,
            "eye": (eye.x, eye.y, eye.z),
            "lp_base_min_x": lp_base_x,
            "lp_top_min_x": lp_top_x,
            "all_passed": all_passed
        })

        status_str = "PASS" if all_passed else "FAIL"
        print(f"[{status_str}] {geom.profile_id}: Wheelbase={wb}m, Eye=({eye.x}, {eye.y}, {eye.z})m, PillarBaseX={lp_base_x:.2f}m, PillarTopX={lp_top_x:.2f}m")
        assert all_passed, f"Sanity check failed for {geom.profile_id}"

    # Generate Fig 9: 3D Coordinate Sanity Diagram
    fig = plt.figure(figsize=(10, 8))
    ax = fig.add_subplot(111, projection='3d')

    sedan_path = os.path.join(config_dir, "representative_sedan_v0.json")
    with open(sedan_path, 'r', encoding='utf-8') as f:
        s_data = json.load(f)
    geom_sedan = load_vehicle_pillars(sedan_path)

    wb = geom_sedan.wheelbase
    f_over = s_data.get("frontOverhangMeters", 0.85)
    r_over = s_data.get("rearOverhangMeters", 0.85)
    w_veh = s_data.get("totalWidthMeters", 1.82)
    h_veh = s_data.get("totalHeightMeters", 1.45)

    # 1. Plot Vehicle Bounding Box
    x_min, x_max = -r_over, wb + f_over
    y_min, y_max = -w_veh / 2.0, w_veh / 2.0
    z_min, z_max = 0.0, h_veh

    # Draw box outline
    ax.plot([x_min, x_max, x_max, x_min, x_min], [y_min, y_min, y_max, y_max, y_min], [0, 0, 0, 0, 0], 'k--', alpha=0.4, label='Vehicle Ground Perimeter')
    ax.plot([x_min, x_max, x_max, x_min, x_min], [y_min, y_min, y_max, y_max, y_min], [z_max, z_max, z_max, z_max, z_max], 'k:', alpha=0.3)

    # 2. Plot Axles
    ax.plot([0, 0], [-geom_sedan.track_width/2, geom_sedan.track_width/2], [0, 0], 'b-', linewidth=3, label='Rear Axle (Origin X=0)')
    ax.plot([wb, wb], [-geom_sedan.track_width/2, geom_sedan.track_width/2], [0, 0], 'g-', linewidth=3, label=f'Front Axle (X={wb}m)')
    ax.scatter([0], [0], [0], color='red', s=80, zorder=5, label='Origin (0,0,0) Rear Axle Center')

    # 3. Plot Driver Eye
    eye = geom_sedan.driver_eye_nominal
    ax.scatter([eye.x], [eye.y], [eye.z], color='magenta', s=100, label=f'Driver Eye ({eye.x}, {eye.y}, {eye.z})m')

    # 4. Plot A-Pillars (Prism wireframe)
    for p, color, p_name in [(geom_sedan.left_pillar, 'cyan', 'Left A-Pillar'), (geom_sedan.right_pillar, 'orange', 'Right A-Pillar')]:
        verts = [[v.to_tuple() for v in (tri.v0, tri.v1, tri.v2)] for tri in p.triangles]
        poly = Poly3DCollection(verts, alpha=0.35, facecolor=color, edgecolor='k', linewidth=0.5)
        ax.add_collection3d(poly)

    # 5. Plot Crosswalk ahead
    cw_x = [12.5, 15.5, 15.5, 12.5, 12.5]
    cw_y = [-4.0, -4.0, 4.0, 4.0, -4.0]
    cw_z = [0.5, 0.5, 0.5, 0.5, 0.5]
    ax.plot(cw_x, cw_y, cw_z, 'r-', linewidth=2, label='Crosswalk Risk Surface (X=14m, Z=0.5m)')

    # 6. Sample Rays from Eye
    for target_y in [-2.0, 0.0, 2.0]:
        ax.plot([eye.x, 14.0], [eye.y, target_y], [eye.z, 0.5], 'y-', alpha=0.5, linewidth=1)

    ax.set_xlabel('X (Forward, meters)')
    ax.set_ylabel('Y (Lateral Left, meters)')
    ax.set_zlabel('Z (Upward, meters)')
    ax.set_title('Figure 9: ISO 8855 Vehicle Coordinate Sanity Layout\n(Rear-Axle Origin, Cabin Eye Placement, Forward A-Pillars)', fontsize=12, fontweight='bold')
    ax.legend(loc='upper right', fontsize=8)
    ax.set_xlim([-1.5, 16.0])
    ax.set_ylim([-5.0, 5.0])
    ax.set_zlim([0.0, 2.0])

    # Mandatory Watermark
    fig.text(0.5, 0.02, "Simulation with synthetic representative parameters. Not validated against a production vehicle.",
             ha='center', fontsize=9, color='darkred', weight='bold')

    fig_dir = os.path.join(os.path.dirname(__file__), "..", "output", "figures")
    os.makedirs(fig_dir, exist_ok=True)
    out_path = os.path.join(fig_dir, "fig9_coordinate_sanity.png")
    plt.tight_layout()
    plt.savefig(out_path, dpi=200)
    plt.close()
    print(f"Generated Figure 9 at: {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == "__main__":
    run_coordinate_sanity_check()
