package roguePlanets.story;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import roguePlanets.AfterlightCaptainScript;

/** Small bridge so the story can show Ellis's portrait. */
public class AfterlightCaptainLookup {
    public static PersonAPI captain() {
        return AfterlightCaptainScript.getDialogueCaptain();
    }
}
