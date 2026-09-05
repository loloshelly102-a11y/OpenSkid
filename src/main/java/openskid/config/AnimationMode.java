package openskid.config;

/**
 * Animation modes for item rendering
 * Original logic by syuto/animations-1.6, integrated into Uzi
 */
public enum AnimationMode {
   VANILLA,
   GRAND,
   FLICK,
   SKID,
   GLIDE,
   PLAIN,
   SPIN,
   AVATAR,
   SWONG,
   SWANG,
   SWANK,
   STYLES,
   NUDGE,
   PUNCH,
   JIGSAW,
   SLIDE,
   SWING,
   OLD,
   PUSH,
   DASH,
   SLASH,
   SCALE,
   SWONK,
   STELLA,
   SMALL,
   EDIT,
   RHYS,
   STAB,
   FLOAT,
   REMIX,
   XIV,
   WINTER,
   YAMATO,
   SLIDE_SWING,
   SMALL_PUSH,
   REVERSE,
   INVENT,
   LEAKED,
   AQUA,
   ASTRO,
   FADEAWAY,
   PASTEL,
   PASTEL_SPIN,
   MOON,
   MOON_PUSH,
   SMOOTH,
   TAP1,
   TAP2,
   SKID3,
   SKID4,
   OPENSKID_1_8,
   OPENSKID_SLIDE,
   OPENSKID_SWANK,
   OPENSKID_SWANG,
   OPENSKID_AVATAR,
   OPENSKID_JIGSAW;

   public static AnimationMode fromJsonValue(String value) {
      try {
         return valueOf(value.toUpperCase());
      } catch (NullPointerException | IllegalArgumentException var2) {
         return VANILLA;
      }
   }
}
