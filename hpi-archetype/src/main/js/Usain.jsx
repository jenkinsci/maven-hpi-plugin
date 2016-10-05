import React, { Component, PropTypes } from 'react';
import * as sse from '@jenkins-cd/sse-gateway';

/**
 * A very simple React component implementing the "jenkins.pipeline.run.result"
 * Blue Ocean extension point. See the "jenkins-js-extension.yaml" file.
 * <p/>
 * It uses the SSE Gateway plugin API to listen for Job run events.
 * It uses these events to change the class name on the rendered
 * element, causing the background image to change as the job state changes.
 * <p/>
 * The SSE Gateway allows the UI to be notified of events happening in the
 * backend, without needing to constantly poll for state data.
 */
export default class Usain extends Component {

    constructor(props) {
        super(props);
        this.run = props.run;
        // Set the original state from the current state of the run.
        this.state = {
            runState: this.run.state,
        };
    }

    componentWillMount() {
        const usain = this;
        // See https://github.com/jenkinsci/sse-gateway-plugin/
        this.jobEventListener = sse.subscribe('job', (event) => {
            // Check the event to make sure it's one we are interested in.
            if (event.jenkins_event === 'job_run_ended'
                && event.blueocean_job_pipeline_name === usain.run.pipeline
                && event.jenkins_object_id === usain.run.id) {
                // If it, we set new state on the React component. This tells
                // React re-render that fragment of the UI.
                usain.setState({
                    runState: 'FINISHED',
                });
            }
        });
    }

    componentWillUnmount() {
        // See https://github.com/jenkinsci/sse-gateway-plugin/
        if (this.jobEventListener) {
            // When the component is "done", we unsubscribe the SSE event
            // listener.
            sse.unsubscribe(this.jobEventListener);
        }
    }

    render() {
        // Just render a simple <div> with a class name derived from the
        // status of the run. We then use CSS (via LESS) to style the component.
        // See src/main/less/extensions.less
        return (<div className="usainbolt">
            <div className={`image ${this.state.runState}`}></div>
            <div className="action-btns">
                <button className={`${this.state.runState === 'RUNNING' ?
                    'btn-primary' : 'btn-secondary'}`}>{this.state.runState}</button>
            </div>
        </div>);
    }
}

Usain.propTypes = {
    run: PropTypes.object,
};
