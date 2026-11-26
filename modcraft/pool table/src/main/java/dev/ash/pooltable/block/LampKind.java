package dev.ash.pooltable.block;

public enum LampKind {
   TIFFANY("tiffany_lamp", 15, "chain", new double[]{8.0, -12.0, 1.5, 8.0, 28.0, 1.5}),
   BANKER("banker_lamp", 15, "chain", new double[]{8.0, -8.0, 1.5, 8.0, 24.0, 1.5}),
   LANTERNS("tavern_lanterns", 15, "chain", new double[]{8.0, -12.0, 1.5, 8.0, 28.0, 1.5}),
   LINEAR("linear_lamp", 15, "cable", new double[]{8.0, -11.0, 0.75, 8.0, 27.0, 0.75}),
   CONES("cone_lamps", 15, "cord", new double[]{8.0, -8.0, 1.0, 8.0, 8.0, 1.0, 8.0, 24.0, 1.0}),
   SAUCERS("saucer_lamps", 14, "cord", new double[]{8.0, -5.0, 1.0, 8.0, 21.0, 1.0}),
   HALO("halo_lamp", 15, null, new double[0]),
   DRONE("drone_lamp", 15, null, new double[0]),
   GRID("neon_grid_lamp", 13, "hull", new double[]{8.0, -8.0, 1.0, 8.0, 24.0, 1.0});

   public final String id;
   public final int light;
   public final String hanger;
   public final double[] hangers;

   private LampKind(String id, int light, String hanger, double[] hangers) {
      this.id = id;
      this.light = light;
      this.hanger = hanger;
      this.hangers = hangers;
   }

   public static LampKind byId(String id) {
      for (LampKind kind : values()) {
         if (kind.id.equals(id)) {
            return kind;
         }
      }

      throw new IllegalArgumentException("no lamp " + id);
   }
}
