package com.example.synthcontroller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class AdsrFragmentTest {

    @Mock
    private PerformActivity mockActivity;
    private AdsrFragment fragment;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        fragment = spy(new AdsrFragment());
    }

    @Test
    public void getters_shouldReturnDefaultValues() {
        assertEquals(50, fragment.getAttackValue());
        assertEquals(100, fragment.getDecayValue());
        assertEquals(180, fragment.getSustainValue());
        assertEquals(100, fragment.getReleaseValue());
    }

    @Test
    public void updateAdsrValues_shouldNotCrashWithNullKnobs() {
        fragment.updateAdsrValues(25, 75, 150, 200);
    }

    @Test
    public void sendCommands_shouldCallActivity() {
        doReturn(mockActivity).when(fragment).getActivity();

        fragment.sendAttackCommand(75);
        verify(mockActivity).sendCommand("ATTACK:", 75);

        fragment.sendDecayCommand(125);
        verify(mockActivity).sendCommand("DECAY:", 125);

        fragment.sendSustainCommand(180);
        verify(mockActivity).sendCommand("SUSTAIN:", 180);

        fragment.sendReleaseCommand(100);
        verify(mockActivity).sendCommand("RELEASE:", 100);
    }
}