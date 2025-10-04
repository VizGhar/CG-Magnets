import { GraphicEntityModule } from './entity-module/GraphicEntityModule.js';
import { ToggleModule } from './toggle-module/ToggleModule.js'
import { TooltipModule } from './tooltip-module/TooltipModule.js';

// List of viewer modules that you want to use in your game
export const modules = [
	GraphicEntityModule,
	ToggleModule,
    TooltipModule
];

// The list of toggles displayed in the options of the viewer
export const options = [
	ToggleModule.defineToggle({
		toggle: 'coords',
		title: 'SHOW COORDINATES',
		values: {
			'ON': true,
			'OFF': false
		},
		default: true
	})
]